// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
// Bounded resource service. No frame capture, rendering, shell execution or polling loop.
#define _GNU_SOURCE 1
#include <android/sharedmem.h>
#include <algorithm>
#include <cerrno>
#include <cinttypes>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <list>
#include <memory>
#include <poll.h>
#include <sstream>
#include <string>
#include <sys/file.h>
#include <sys/inotify.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>
#include <vector>

namespace {
constexpr const char* SOCKET_NAME="c17.performance.cache.v1";
constexpr const char* DATA="/data/adb/c17-performance";
constexpr size_t MIB=1024*1024, MAX_ENTRY=32*MIB, MEMORY_LIMIT=64*MIB, DISK_LIMIT=256*MIB;
constexpr uint32_t MAGIC=0x43313731;
volatile sig_atomic_t running=1;
volatile sig_atomic_t expired=0;
void stop(int){running=0;}
void expire(int){expired=1;}
struct Fd {
    int n=-1;
    explicit Fd(int value=-1):n(value){}
    ~Fd(){if(n>=0)close(n);}
    Fd(const Fd&)=delete; Fd& operator=(const Fd&)=delete;
    Fd(Fd&& other) noexcept:n(other.n){other.n=-1;}
    Fd& operator=(Fd&& other) noexcept {if(this!=&other){if(n>=0)close(n);n=other.n;other.n=-1;}return *this;}
};
bool writeAll(int fd,const void* data,size_t count){
    const auto* p=static_cast<const uint8_t*>(data);
    while(count){if(expired)return false;ssize_t n=write(fd,p,count);if(n<0&&errno==EINTR)continue;if(n<=0)return false;p+=n;count-=n;}return true;
}
bool readAll(int fd,void* data,size_t count){
    auto* p=static_cast<uint8_t*>(data);
    while(count){if(expired)return false;ssize_t n=read(fd,p,count);if(n<0&&errno==EINTR)continue;if(n<=0)return false;p+=n;count-=n;}return true;
}
bool line(int fd,std::string& out,size_t limit=256){
    out.clear();char c;
    while(out.size()<limit){if(expired)return false;ssize_t n=read(fd,&c,1);if(n<0&&errno==EINTR)continue;if(n!=1)return false;
        if(c=='\n')return true;if(c<' '||c>'~')return false;out.push_back(c);}return false;
}
uint32_t crc32(const void* raw,size_t size){
    static uint32_t table[256];static bool initialized=false;
    if(!initialized){for(uint32_t i=0;i<256;i++){uint32_t v=i;for(int j=0;j<8;j++)v=(v>>1)^(0xedb88320u&uint32_t(-int(v&1)));table[i]=v;}initialized=true;}
    uint32_t crc=~0u;const auto* p=static_cast<const uint8_t*>(raw);
    for(size_t i=0;i<size;i++)crc=(crc>>8)^table[(crc^p[i])&255];return ~crc;
}
bool keyValid(const std::string& s){return s.size()==64&&s.find_first_not_of("0123456789abcdef")==std::string::npos;}
bool safeDirectory(const std::string& path,bool create){
    if(path.empty()||path[0]!='/')return false;size_t offset=1;
    for(;;){size_t slash=path.find('/',offset);std::string part=path.substr(0,slash);struct stat st{};
        if(lstat(part.c_str(),&st)<0){if(!create||errno!=ENOENT||mkdir(part.c_str(),0700)<0)return false;if(lstat(part.c_str(),&st)<0)return false;}
        if(!S_ISDIR(st.st_mode)||S_ISLNK(st.st_mode))return false;
        if(part.size()>=strlen(DATA)&&part.compare(0,strlen(DATA),DATA)==0&&(st.st_uid!=0||(st.st_mode&0022)))return false;
        if(slash==std::string::npos)return true;offset=slash+1;
    }
}
struct Entry {
    uid_t uid=0;std::string key;size_t size=0;uint32_t crc=0;bool core=false;Fd fd;void* mapping=MAP_FAILED;
    ~Entry(){if(mapping!=MAP_FAILED)munmap(mapping,size);}
    bool allocate(size_t bytes){
        if(!bytes||bytes>MAX_ENTRY)return false;size=bytes;fd=Fd(ASharedMemory_create("c17-resource",bytes));
        if(fd.n<0)return false;mapping=mmap(nullptr,bytes,PROT_READ|PROT_WRITE,MAP_SHARED,fd.n,0);return mapping!=MAP_FAILED;
    }
    bool seal(){crc=crc32(mapping,size);return mprotect(mapping,size,PROT_READ)==0&&ASharedMemory_setProt(fd.n,PROT_READ)==0;}
};
struct DiskHeader {uint32_t magic,version,bytes,crc,core;};
int oomScore=0;
std::string memoryLow="unsupported";
struct Store {
    std::string folder;std::list<std::unique_ptr<Entry>> entries;size_t bytes=0;
    uint64_t hits=0,misses=0,writes=0,diskHits=0,evictions=0,rejected=0;
    explicit Store(std::string path):folder(std::move(path)){}
    std::string file(uid_t uid,const std::string& key){return folder+"/"+std::to_string(uid)+"-"+key+".bin";}
    Entry* find(uid_t uid,const std::string& key){
        for(auto it=entries.begin();it!=entries.end();++it)if((*it)->uid==uid&&(*it)->key==key){
            entries.splice(entries.begin(),entries,it);return entries.front().get();}return nullptr;
    }
    void erase(std::list<std::unique_ptr<Entry>>::iterator it){bytes-=(*it)->size;entries.erase(it);evictions++;}
    Entry* add(std::unique_ptr<Entry> entry){
        // The key names immutable content/version. A duplicate PUT cannot change existing consumers.
        if(Entry* old=find(entry->uid,entry->key)){
            if(old->size!=entry->size||old->crc!=entry->crc||memcmp(old->mapping,entry->mapping,old->size)){rejected++;return nullptr;}
            old->core=old->core||entry->core;return old;
        }
        while(bytes+entry->size>MEMORY_LIMIT||entries.size()>=256){
            auto victim=entries.end();for(auto it=entries.end();it!=entries.begin();){--it;if(!(*it)->core){victim=it;break;}}
            if(victim==entries.end())victim=std::prev(entries.end());erase(victim);
        }
        bytes+=entry->size;entries.push_front(std::move(entry));return entries.front().get();
    }
    bool save(const Entry& entry){
        std::string target=file(entry.uid,entry.key),pattern=folder+"/.new.XXXXXX";
        std::vector<char> temp(pattern.begin(),pattern.end());temp.push_back(0);Fd output(mkstemp(temp.data()));if(output.n<0)return false;
        DiskHeader header{MAGIC,2,uint32_t(entry.size),entry.crc,entry.core?1u:0u};
        bool ok=fchmod(output.n,0600)==0&&writeAll(output.n,&header,sizeof(header))&&writeAll(output.n,entry.mapping,entry.size)
            &&fsync(output.n)==0&&rename(temp.data(),target.c_str())==0;
        if(!ok)unlink(temp.data());else pruneDisk();return ok;
    }
    Entry* get(uid_t uid,const std::string& key,bool account=true){
        if(Entry* ready=find(uid,key)){if(account)hits++;return ready;}
        std::string path=file(uid,key);Fd input(open(path.c_str(),O_RDONLY|O_CLOEXEC|O_NOFOLLOW));
        if(input.n<0){if(account)misses++;return nullptr;}struct stat st{};DiskHeader header{};
        auto entry=std::make_unique<Entry>();entry->uid=uid;entry->key=key;
        if(fstat(input.n,&st)<0||!S_ISREG(st.st_mode)||st.st_uid!=geteuid()||(st.st_mode&0022)||st.st_nlink!=1
            ||!readAll(input.n,&header,sizeof(header))||header.magic!=MAGIC||header.version!=2||header.core>1||header.bytes>MAX_ENTRY||!header.bytes
            ||st.st_size!=off_t(sizeof(header)+header.bytes)||!entry->allocate(header.bytes)
            ||!readAll(input.n,entry->mapping,entry->size)||!entry->seal()||entry->crc!=header.crc){
            if(account)misses++;rejected++;unlink(path.c_str());return nullptr;
        }
        entry->core=header.core!=0;if(account){diskHits++;hits++;}return add(std::move(entry));
    }
    void trim(uid_t uid,bool severe){for(auto it=entries.begin();it!=entries.end();)if((*it)->uid==uid&&(severe||!(*it)->core)){auto old=it++;erase(old);}else ++it;}
    void pruneDisk(){
        struct Record{std::string name;int64_t size,time;};std::vector<Record> records;uint64_t total=0;
        DIR* dir=opendir(folder.c_str());if(!dir)return;
        while(dirent* item=readdir(dir)){std::string name=item->d_name;size_t dash=name.find('-');
            if(name.compare(0,5,".new.")==0){struct stat st{};std::string path=folder+"/"+name;
                if(lstat(path.c_str(),&st)==0&&S_ISREG(st.st_mode)&&st.st_uid==geteuid())unlink(path.c_str());continue;}
            if(dash==std::string::npos||name.substr(0,dash).find_first_not_of("0123456789")!=std::string::npos
                ||name.size()!=dash+69||name.substr(name.size()-4)!=".bin"||!keyValid(name.substr(dash+1,64)))continue;
            struct stat st{};std::string path=folder+"/"+name;if(lstat(path.c_str(),&st)<0||!S_ISREG(st.st_mode)||st.st_uid!=geteuid())continue;
            records.push_back({name,st.st_size,st.st_mtime});total+=st.st_size;
        }closedir(dir);std::sort(records.begin(),records.end(),[](const Record& a,const Record& b){return a.time<b.time;});
        size_t count=records.size();for(const auto& record:records){if(total<=DISK_LIMIT&&count<=512)break;
            if(unlink((folder+"/"+record.name).c_str())==0){total-=record.size;count--;}}
    }
    std::string status(){std::ostringstream out;out<<"cache_running=1\ncache_protocol=1\ncache_pid="<<getpid()
        <<"\ncache_bytes="<<bytes<<"\ncache_limit="<<MEMORY_LIMIT<<"\ncache_entries="<<entries.size()
        <<"\ncache_hits="<<hits<<"\ncache_misses="<<misses<<"\ncache_disk_hits="<<diskHits
        <<"\ncache_writes="<<writes<<"\ncache_evictions="<<evictions<<"\ncache_rejected="<<rejected
        <<"\ncache_oom_adj="<<oomScore<<"\ncache_memory_low="<<memoryLow<<"\n";return out.str();}
};
std::string processName(pid_t pid){char value[256]{};Fd fd(open(("/proc/"+std::to_string(pid)+"/cmdline").c_str(),O_RDONLY|O_CLOEXEC));
    if(fd.n<0)return {};ssize_t n=read(fd.n,value,sizeof(value)-1);return n>0?std::string(value,strnlen(value,size_t(n))):std::string();}
bool allowed(const ucred& peer){
    if(peer.uid==0)return true;std::string name=processName(peer.pid);size_t colon=name.find(':');if(colon!=std::string::npos)name.resize(colon);
    // Root-owned list is generated from PackageManager; no client-supplied identity is accepted.
    Fd fd(open((std::string(DATA)+"/cache-clients").c_str(),O_RDONLY|O_CLOEXEC|O_NOFOLLOW));struct stat st{};
    if(fd.n<0||fstat(fd.n,&st)<0||!S_ISREG(st.st_mode)||st.st_uid!=0||(st.st_mode&0022)||st.st_size>8192)return false;
    std::string record;while(line(fd.n,record)){std::istringstream in(record);unsigned long uid;std::string pkg,extra;
        if(in>>uid>>pkg&&!(in>>extra)&&uid==peer.uid&&pkg==name)return true;}return false;
}
sockaddr_un address(){sockaddr_un addr{};addr.sun_family=AF_UNIX;strncpy(addr.sun_path+1,SOCKET_NAME,sizeof(addr.sun_path)-2);return addr;}
socklen_t addressSize(){return offsetof(sockaddr_un,sun_path)+1+strlen(SOCKET_NAME);}
bool sendEntry(int socket,const Entry& entry){
    std::string header="OK "+std::to_string(entry.size)+" "+std::to_string(entry.crc)+"\n";
    iovec io{const_cast<char*>(header.data()),header.size()};alignas(cmsghdr) char control[CMSG_SPACE(sizeof(int))]{};
    msghdr message{};message.msg_iov=&io;message.msg_iovlen=1;message.msg_control=control;message.msg_controllen=sizeof(control);
    cmsghdr* c=CMSG_FIRSTHDR(&message);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(int));memcpy(CMSG_DATA(c),&entry.fd.n,sizeof(int));
    ssize_t n;do{n=sendmsg(socket,&message,MSG_NOSIGNAL);}while(n<0&&errno==EINTR);
    return n>0&&(size_t(n)==header.size()||writeAll(socket,header.data()+n,header.size()-size_t(n)));
}
void serveClient(int fd,Store& store){
    timeval deadline{2,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&deadline,sizeof(deadline));setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&deadline,sizeof(deadline));
    ucred peer{};socklen_t length=sizeof(peer);if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&length)<0||!allowed(peer)){store.rejected++;writeAll(fd,"DENIED\n",7);return;}
    std::string request;if(!line(fd,request))return;std::istringstream input(request);std::string protocol,verb,key,extra;
    if(!(input>>protocol>>verb)||protocol!="C17C1"){writeAll(fd,"VERSION\n",8);return;}
    if(verb=="STATUS"&&!(input>>extra)){auto text=store.status()+"END\n";writeAll(fd,text.data(),text.size());return;}
    if(verb=="STOP"&&peer.uid==0&&!(input>>extra)){writeAll(fd,"OK\n",3);running=0;return;}
    if(verb=="TRIM"){int severe=0;if(input>>severe&&!(input>>extra)&&(severe==0||severe==1)){store.trim(peer.uid,severe);writeAll(fd,"OK\n",3);}return;}
    if(!(input>>key)||!keyValid(key)){writeAll(fd,"INVALID\n",8);return;}
    if((verb=="GET"||verb=="HAS")&&!(input>>extra)){Entry* entry=store.get(peer.uid,key,verb=="GET");
        if(!entry)writeAll(fd,"MISS\n",5);else if(verb=="HAS")writeAll(fd,"OK\n",3);else sendEntry(fd,*entry);return;}
    if(verb=="PUT"){
        uint64_t count=0;unsigned core=0;if(!(input>>count>>core)||input>>extra||!count||count>MAX_ENTRY||core>1){writeAll(fd,"INVALID\n",8);return;}
        auto entry=std::make_unique<Entry>();entry->uid=peer.uid;entry->key=key;entry->core=core;
        if(!entry->allocate(size_t(count))||!readAll(fd,entry->mapping,entry->size)||!entry->seal()){store.rejected++;return;}
        // Reload a persisted key before comparing; service restarts must not weaken immutability.
        store.get(peer.uid,key,false);
        Entry* ready=store.add(std::move(entry));if(!ready){writeAll(fd,"CONFLICT\n",9);return;}
        bool persisted=store.save(*ready);store.writes++;writeAll(fd,persisted?"OK\n":"MEMORY\n",persisted?3:7);return;
    }
    writeAll(fd,"INVALID\n",8);
}
int client(const char* verb){
    Fd socket(::socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0));auto addr=address();
    if(socket.n<0||connect(socket.n,reinterpret_cast<sockaddr*>(&addr),addressSize())<0){puts("cache_running=0");return 1;}
    ucred peer{};socklen_t length=sizeof(peer);
    if(getsockopt(socket.n,SOL_SOCKET,SO_PEERCRED,&peer,&length)<0||peer.uid!=0){puts("cache_running=0");return 1;}
    timeval deadline{2,0};setsockopt(socket.n,SOL_SOCKET,SO_RCVTIMEO,&deadline,sizeof(deadline));
    std::string request=std::string("C17C1 ")+verb+"\n";if(!writeAll(socket.n,request.data(),request.size()))return 1;
    char buffer[4096];ssize_t size;while((size=read(socket.n,buffer,sizeof(buffer)))>0)if(!writeAll(STDOUT_FILENO,buffer,size))return 1;return size==0?0:1;
}
int selfTest(){
    if(crc32("123456789",9)!=0xcbf43926u||keyValid("../bad")||!keyValid(std::string(64,'a')))return 10;
    char temporary[]="/data/local/tmp/c17-cache-check.XXXXXX";char* path=mkdtemp(temporary);if(!path)return 11;
    std::string dir=path;int result=0;
    {
        Store store(dir);auto e=std::make_unique<Entry>();e->uid=geteuid();e->key=std::string(64,'a');e->core=true;
        if(!e->allocate(9)){result=12;}else{memcpy(e->mapping,"123456789",9);if(!e->seal())result=13;}
        if(!result){Entry* saved=store.add(std::move(e));if(!saved||!store.save(*saved))result=14;
            store.trim(geteuid(),false);if(store.entries.size()!=1)result=15;
            store.trim(geteuid(),true);Entry* loaded=store.get(geteuid(),std::string(64,'a'));
            if(!loaded||memcmp(loaded->mapping,"123456789",9)||store.diskHits!=1)result=16;
            if(loaded){int pair[2];if(socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair)<0)result=17;
                else{Fd a(pair[0]),b(pair[1]);if(!sendEntry(a.n,*loaded))result=18;
                    char data[64]{};iovec io{data,sizeof(data)};alignas(cmsghdr) char control[CMSG_SPACE(sizeof(int))]{};
                    msghdr message{};message.msg_iov=&io;message.msg_iovlen=1;message.msg_control=control;message.msg_controllen=sizeof(control);
                    if(recvmsg(b.n,&message,0)<=0)result=19;cmsghdr* c=CMSG_FIRSTHDR(&message);
                    if(!c||c->cmsg_type!=SCM_RIGHTS)result=20;else{int received;memcpy(&received,CMSG_DATA(c),sizeof(received));Fd fd(received);
                        void* mapping=mmap(nullptr,9,PROT_READ,MAP_SHARED,fd.n,0);if(mapping==MAP_FAILED)result=21;else{if(memcmp(mapping,"123456789",9))result=22;munmap(mapping,9);}
                        void* writable=mmap(nullptr,9,PROT_READ|PROT_WRITE,MAP_SHARED,fd.n,0);if(writable!=MAP_FAILED){result=23;munmap(writable,9);}
                        if(pwrite(fd.n,"bad",3,0)>=0)result=25;}}}
            auto duplicate=std::make_unique<Entry>();duplicate->uid=geteuid();duplicate->key=std::string(64,'a');duplicate->allocate(9);
            memcpy(duplicate->mapping,"abcdefghi",9);duplicate->seal();if(store.add(std::move(duplicate)))result=24;
        }
        unlink(store.file(geteuid(),std::string(64,'a')).c_str());
    }
    rmdir(path);printf("cache self-test result=%d\n",result);return result;
}
bool writeNode(const std::string& path,const std::string& value){Fd fd(open(path.c_str(),O_WRONLY|O_CLOEXEC|O_NOFOLLOW));return fd.n>=0&&writeAll(fd.n,value.data(),value.size());}
std::string retention(){
    // This process has a hard resource budget; it never changes SystemUI or global LMK policy.
    if(writeNode("/proc/self/oom_score_adj","-600"))oomScore=-600;
    const std::string group="/sys/fs/cgroup/c17-cache";
    // Do not enable controllers or alter a parent/OEM process group.
    Fd control(open("/sys/fs/cgroup/cgroup.subtree_control",O_RDONLY|O_CLOEXEC));char value[256]{};
    if(control.n<0||read(control.n,value,255)<=0||!strstr(value,"memory"))return {};
    if(mkdir(group.c_str(),0700)<0&&errno!=EEXIST)return {};
    struct stat st{};if(lstat(group.c_str(),&st)<0||!S_ISDIR(st.st_mode)||st.st_uid!=0)return {};
    if(!writeNode(group+"/memory.low","16777216")||!writeNode(group+"/cgroup.procs",std::to_string(getpid()))){rmdir(group.c_str());return {};}
    memoryLow="16777216";return group;
}
}
int main(int argc,char** argv){
    signal(SIGPIPE,SIG_IGN);if(argc!=2)return 2;
    if(!strcmp(argv[1],"--self-test"))return selfTest();
    if(!strcmp(argv[1],"--status"))return client("STATUS");
    if(!strcmp(argv[1],"--stop")){if(geteuid()!=0)return 3;return client("STOP");}
    if(strcmp(argv[1],"--serve")||geteuid()!=0)return 3;
    umask(0077);std::string directory=std::string(DATA)+"/cache-v1";if(!safeDirectory(directory,true))return 4;
    Fd lock(open((std::string(DATA)+"/cache-service.lock").c_str(),O_CREAT|O_CLOEXEC|O_NOFOLLOW|O_RDWR,0600));
    struct stat lockStat{};if(lock.n<0||fstat(lock.n,&lockStat)<0||!S_ISREG(lockStat.st_mode)||lockStat.st_uid!=0||(lockStat.st_mode&0022)||flock(lock.n,LOCK_EX|LOCK_NB)<0)return 5;
    Fd socket(::socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0));auto addr=address();
    if(socket.n<0||bind(socket.n,reinterpret_cast<sockaddr*>(&addr),addressSize())<0||listen(socket.n,8)<0)return 6;
    struct sigaction handler{};handler.sa_handler=stop;sigaction(SIGTERM,&handler,nullptr);sigaction(SIGINT,&handler,nullptr);
    handler.sa_handler=expire;sigaction(SIGALRM,&handler,nullptr);setpriority(PRIO_PROCESS,0,10);
    char executable[4096]{};ssize_t executableSize=readlink("/proc/self/exe",executable,sizeof(executable)-1);
    if(executableSize<=0)return 7;
    std::string module=executable;size_t bin=module.rfind("/bin/");if(bin==std::string::npos)return 7;module.resize(bin);
    if(module!="/data/adb/modules/c17_performance"&&module!="/data/adb/modules_update/c17_performance")return 7;
    Fd events(inotify_init1(IN_CLOEXEC|IN_NONBLOCK));
    if(events.n<0||inotify_add_watch(events.n,module.c_str(),IN_CREATE|IN_MOVED_TO|IN_DELETE_SELF|IN_MOVE_SELF)<0)return 7;
    if(access((module+"/disable").c_str(),F_OK)==0||access((module+"/remove").c_str(),F_OK)==0)return 7;
    std::string group=retention();
    Store store(directory);store.pruneDisk();
    while(running){pollfd event[2]={{socket.n,POLLIN,0},{events.n,POLLIN,0}};int n=poll(event,2,-1);if(n<0&&errno==EINTR)continue;if(n<0)break;
        if(event[1].revents){alignas(inotify_event) char buffer[4096];ssize_t count=read(events.n,buffer,sizeof(buffer));
            for(ssize_t i=0;i<count;){auto* item=reinterpret_cast<inotify_event*>(buffer+i);if(item->mask&(IN_DELETE_SELF|IN_MOVE_SELF|IN_IGNORED))running=0;i+=sizeof(inotify_event)+item->len;}
            if(access((module+"/disable").c_str(),F_OK)==0||access((module+"/remove").c_str(),F_OK)==0)running=0;
        }
        if(running&&(event[0].revents&POLLIN)){Fd connection(accept4(socket.n,nullptr,nullptr,SOCK_CLOEXEC));
            if(connection.n>=0){expired=0;alarm(5);serveClient(connection.n,store);alarm(0);expired=0;}}}
    if(!group.empty()){writeNode("/sys/fs/cgroup/cgroup.procs",std::to_string(getpid()));rmdir(group.c_str());}
    return 0;
}
