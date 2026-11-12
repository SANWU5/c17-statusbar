// SPDX-License-Identifier: GPL-3.0-only
// Fixed-purpose early-boot physical-key watcher. Never grabs an input device and never
// accepts a path, package, command, synthetic event or force-trigger option from callers.
#include "sequence.h"
#include "capabilities.h"
#include <algorithm>
#include <array>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <string>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#include <vector>

namespace {
using c17rescue::Sequence;
using Tick = c17rescue::Tick;
constexpr const char* kModule = "/data/adb/modules/c17_performance";
constexpr const char* kData = "/data/adb/c17-performance";
volatile sig_atomic_t stopped = 0;
void stopSignal(int) { stopped = 1; }
Tick clockMs() { timespec t{}; if (clock_gettime(CLOCK_MONOTONIC, &t)) return 0; return Tick(t.tv_sec)*1000+Tick(t.tv_nsec)/1000000; }
std::string property(const char* key) { char text[PROP_VALUE_MAX]{}; __system_property_get(key,text); return text; }
bool bootRunning() { return property("sys.boot_completed")!="1" && property("init.svc.bootanim")=="running"; }
bool exists(const char* path) { struct stat s{}; return lstat(path,&s)==0 || errno!=ENOENT; }
bool disabled() {
    return exists("/data/adb/modules/c17_performance/disable") || exists("/data/adb/modules/c17_performance/remove")
            || exists("/data/adb/c17-performance/boot-rescue-disabled");
}
bool secure(int fd, bool dir) {
    struct stat s{};
    return fd>=0 && fstat(fd,&s)==0 && (dir?S_ISDIR(s.st_mode):S_ISREG(s.st_mode))
            && s.st_uid==0 && !(s.st_mode&0022) && (dir || s.st_nlink==1);
}
int directoryAt(int parent,const char* name) {
    int fd=openat(parent,name,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
    if (!secure(fd,true)) { if(fd>=0)close(fd);return -1; } return fd;
}
// /data itself is owned by system on Android. /data/adb (root-owned and not
// group/world writable) is the trust boundary; no component may be a symlink.
int adbDirectory() {
    int data=open("/data",O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
    struct stat s{};
    if(data<0 || fstat(data,&s) || !S_ISDIR(s.st_mode) || (s.st_uid!=0 && s.st_uid!=1000)) { if(data>=0)close(data);return -1; }
    int fd=directoryAt(data,"adb");close(data);return fd;
}
int moduleDirectory() {
    int adb=adbDirectory();if(adb<0)return -1;
    int modules=directoryAt(adb,"modules");close(adb);if(modules<0)return -1;
    int module=directoryAt(modules,"c17_performance");close(modules);return module;
}
int dataDirectory(bool create) {
    int adb=adbDirectory();if(adb<0)return -1;
    if(create && mkdirat(adb,"c17-performance",0700) && errno!=EEXIST) { close(adb);return -1; }
    int fd=directoryAt(adb,"c17-performance");close(adb);return fd;
}
std::string readSmall(int fd, size_t maximum) {
    std::string text;char buffer[256];
    for(;;) { ssize_t n=read(fd,buffer,sizeof buffer);if(n<0&&errno==EINTR)continue;if(n<0)return {};if(!n)return text;
        if(text.size()+size_t(n)>maximum)return {};text.append(buffer,size_t(n)); }
}
std::string bootId() {
    int fd=open("/proc/sys/kernel/random/boot_id",O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return {};
    std::string id=readSmall(fd,64);close(fd);if(!id.empty()&&id.back()=='\n')id.pop_back();
    if(id.size()!=36)return {};
    for(size_t n=0;n<id.size();++n) { if(n==8||n==13||n==18||n==23) { if(id[n]!='-')return {}; }
        else if(!((id[n]>='0'&&id[n]<='9')||(id[n]>='a'&&id[n]<='f')))return {}; }
    return id;
}
bool writeAll(int fd,const std::string& text) {
    size_t done=0;while(done<text.size()) { ssize_t n=write(fd,text.data()+done,text.size()-done);
        if(n<0&&errno==EINTR)continue;if(n<=0)return false;done+=size_t(n); }return fsync(fd)==0;
}
bool safeExisting(int directory,const char* name) {
    int fd=openat(directory,name,O_RDONLY|O_NOFOLLOW|O_CLOEXEC);
    if(fd<0)return errno==ENOENT;
    bool result=secure(fd,false);close(fd);return result;
}
bool writePid(int directory,const std::string& boot) {
    if(!safeExisting(directory,"boot-rescue-watch.pid"))return false;
    std::string temporary=".boot-rescue-watch."+std::to_string(getpid());
    int fd=openat(directory,temporary.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600);
    if(fd<0)return false;
    bool ok=secure(fd,false)&&writeAll(fd,std::to_string(getpid())+"\n"+boot+"\n");close(fd);
    if(ok)ok=renameat(directory,temporary.c_str(),directory,"boot-rescue-watch.pid")==0;
    if(!ok)unlinkat(directory,temporary.c_str(),0);return ok;
}
void removePid(int directory,const std::string& boot) {
    int fd=openat(directory,"boot-rescue-watch.pid",O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return;
    bool own=secure(fd,false)&&readSmall(fd,128)==std::to_string(getpid())+"\n"+boot+"\n";close(fd);
    if(own)unlinkat(directory,"boot-rescue-watch.pid",0);
}
bool runRecovery(int directory,const std::string& boot) {
    if(disabled()||!bootRunning()||boot!=bootId())return false;
    int module=moduleDirectory();if(module<0)return false;
    int metadata=openat(module,"module.prop",O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
    bool valid=secure(metadata,false);
    std::string text=valid?readSmall(metadata,16384):"";if(metadata>=0)close(metadata);
    valid=valid && (text.rfind("id=c17_performance\n",0)==0 || text.find("\nid=c17_performance\n")!=std::string::npos);
    int script=valid?openat(module,"boot-rescue.sh",O_RDONLY|O_CLOEXEC|O_NOFOLLOW):-1;close(module);
    if(!secure(script,false)) { if(script>=0)close(script);return false; }
    std::string marker="boot-rescue."+boot;
    int stamp=openat(directory,marker.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600);
    if(stamp<0) { close(script);return false; }
    bool written=secure(stamp,false)&&writeAll(stamp,"authorized\n"+boot+"\n");close(stamp);
    // An incomplete stamp stays present: failures must not repeatedly reset a boot.
    if(!written || disabled() || !bootRunning()) { close(script);return false; }
    pid_t child=fork();
    if(child==0) {
        if(script!=3 && dup2(script,3)<0)_exit(126);
        if(fcntl(3,F_SETFD,0)<0)_exit(126);
        int nullfd=open("/dev/null",O_RDWR|O_CLOEXEC|O_NOFOLLOW);
        if(nullfd<0)_exit(126);
        for(int fd=0;fd<3;++fd)if(dup2(nullfd,fd)<0)_exit(126);
        if(nullfd>3)close(nullfd);
        execl("/system/bin/sh","sh","/proc/self/fd/3","--trigger",static_cast<char*>(nullptr));_exit(127);
    }
    close(script);if(child<0)return false;
    int result=0;while(waitpid(child,&result,0)<0) { if(errno!=EINTR)return false; }
    return WIFEXITED(result)&&WEXITSTATUS(result)==0;
}

constexpr size_t kBits=(KEY_MAX+8)/8;
bool hasBit(const unsigned char* bits,int code) { return bits[code/8]&(1U<<(code%8)); }
struct Device { int fd=-1;std::string name;bool syncLost=false; };
struct Sample { Tick time;int device;input_event event; };
bool eventName(const char* name) {
    if(strncmp(name,"event",5)||!name[5])return false;
    for(const char* p=name+5;*p;++p)if(*p<'0'||*p>'9')return false;return true;
}
int inputDirectory() { return open("/dev/input",O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW); }
bool keyDevice(int fd) {
    struct stat s{};unsigned char bits[kBits]{};
    return fstat(fd,&s)==0 && S_ISCHR(s.st_mode) && ioctl(fd,EVIOCGBIT(EV_KEY,sizeof bits),bits)>=0
            && (hasBit(bits,KEY_POWER)||hasBit(bits,KEY_VOLUMEUP)||hasBit(bits,KEY_VOLUMEDOWN));
}
c17rescue::ProbeCapabilities probeCapabilities() {
    c17rescue::ProbeCapabilities result;
    int fd=inputDirectory();if(fd<0)return result;DIR* d=fdopendir(fd);if(!d){close(fd);return result;}
    while(dirent* item=readdir(d))if(eventName(item->d_name)) {
        int key=openat(dirfd(d),item->d_name,O_RDONLY|O_NONBLOCK|O_NOFOLLOW|O_CLOEXEC);
        if(key>=0) {
            bool readable=keyDevice(key);int clock=CLOCK_MONOTONIC;
            bool monotonic=readable && ioctl(key,EVIOCSCLOCKID,&clock)==0;
            unsigned char bits[kBits]{};unsigned coverage=0;
            if(monotonic && ioctl(key,EVIOCGBIT(EV_KEY,sizeof bits),bits)>=0) {
                coverage=(hasBit(bits,KEY_POWER)?1U:0U)|(hasBit(bits,KEY_VOLUMEUP)?2U:0U)|(hasBit(bits,KEY_VOLUMEDOWN)?4U:0U);
            }
            result.observe(readable,monotonic,coverage);close(key);
        }
    }
    closedir(d);return result;
}
void scan(std::array<Device,Sequence::kDevices>& devices,Sequence& sequence) {
    int fd=inputDirectory();if(fd<0)return;DIR* d=fdopendir(fd);if(!d){close(fd);return;}
    while(dirent* item=readdir(d))if(eventName(item->d_name)) {
        bool known=false;int free=-1;
        for(int n=0;n<Sequence::kDevices;++n) { if(devices[n].name==item->d_name && devices[n].fd>=0)known=true;if(devices[n].fd<0&&free<0)free=n; }
        if(known||free<0)continue;
        int input=openat(dirfd(d),item->d_name,O_RDONLY|O_NONBLOCK|O_NOFOLLOW|O_CLOEXEC);
        if(input<0)continue;
        int clock=CLOCK_MONOTONIC;
        if(!keyDevice(input)||ioctl(input,EVIOCSCLOCKID,&clock)<0) {close(input);continue;}
        devices[free].fd=input;devices[free].name=item->d_name;sequence.dropped(free);
        unsigned char pressed[kBits]{};
        if(ioctl(input,EVIOCGKEY(sizeof pressed),pressed)>=0)for(int code:{KEY_POWER,KEY_VOLUMEUP,KEY_VOLUMEDOWN})if(hasBit(pressed,code)) {
            int index=Sequence::keyIndex(code);sequence.held[free]|=1U<<index;++sequence.counts[index];
        }
    }
    closedir(d);
}
int watch() {
    if(getuid()!=0)return 21;
    umask(0077);
    if(disabled()||property("sys.boot_completed")=="1")return 0;
    int module=moduleDirectory();if(module<0)return 20;close(module);
    int directory=dataDirectory(true);if(directory<0)return 20;
    int lock=openat(directory,"boot-rescue-watch.lock",O_RDWR|O_CREAT|O_NOFOLLOW|O_CLOEXEC,0600);
    if(!secure(lock,false)) {if(lock>=0)close(lock);close(directory);return 20;}
    if(flock(lock,LOCK_EX|LOCK_NB)) {close(lock);close(directory);return errno==EWOULDBLOCK?0:20;}
    std::string boot=bootId();if(boot.empty()) {close(lock);close(directory);return 20;}
    std::string marker="boot-rescue."+boot;
    struct stat marked{};if(fstatat(directory,marker.c_str(),&marked,AT_SYMLINK_NOFOLLOW)==0 || errno!=ENOENT) {close(lock);close(directory);return 0;}
    if(!writePid(directory,boot)) {close(lock);close(directory);return 20;}
    signal(SIGTERM,stopSignal);signal(SIGINT,stopSignal);signal(SIGHUP,stopSignal);
    std::array<Device,Sequence::kDevices> devices;
    Sequence sequence;bool seenRunning=false;Tick began=clockMs(),lastScan=0,armedAt=0;
    int result=0;
    while(!stopped) {
        Tick now=clockMs();
        if(!now||now-began>600000||disabled()||property("sys.boot_completed")=="1"||boot!=bootId())break;
        bool running=property("init.svc.bootanim")=="running";
        if(!running) {if(seenRunning||now-began>=30000)break;poll(nullptr,0,100);continue;}
        if(!seenRunning) {seenRunning=true;armedAt=now;}
        if(!lastScan||now-lastScan>=1000) {scan(devices,sequence);lastScan=now;}
        std::array<pollfd,Sequence::kDevices> polls{};
        for(int n=0;n<Sequence::kDevices;++n)polls[n]={devices[n].fd,POLLIN,0};
        int ready=poll(polls.data(),polls.size(),100);if(ready<0) {if(errno==EINTR)continue;result=22;break;}
        std::vector<Sample> events;events.reserve(64);bool overflow=false;
        for(int n=0;n<Sequence::kDevices;++n)if(devices[n].fd>=0) {
            if(polls[n].revents&(POLLHUP|POLLERR|POLLNVAL)) {close(devices[n].fd);devices[n].fd=-1;devices[n].name.clear();sequence.dropped(n);continue;}
            if(!(polls[n].revents&POLLIN))continue;
            input_event batch[32];
            for(int pass=0;pass<128;++pass) {
                ssize_t bytes=read(devices[n].fd,batch,sizeof batch);
                if(bytes<0&&errno==EINTR){--pass;continue;}
                if(bytes<0&&(errno==EAGAIN||errno==EWOULDBLOCK))break;
                if(bytes<=0||bytes%sizeof(input_event)) {sequence.dropped(n);break;}
                for(size_t e=0;e<size_t(bytes)/sizeof(input_event);++e) {
                    Tick time=Tick(batch[e].time.tv_sec)*1000+Tick(batch[e].time.tv_usec)/1000;
                    if(time<armedAt||time>clockMs()+500)continue;
                    if(events.size()>=4096) {overflow=true;continue;}
                    events.push_back({time,n,batch[e]});
                }
            }
        }
        if(overflow) {sequence=Sequence{};continue;}
        std::stable_sort(events.begin(),events.end(),[](const Sample& a,const Sample& b){return a.time<b.time;});
        for(const Sample& e:events) {
            if(e.event.type==EV_SYN && e.event.code==SYN_DROPPED) {sequence.dropped(e.device);devices[e.device].syncLost=true;continue;}
            if(devices[e.device].syncLost) {
                if(e.event.type==EV_SYN && e.event.code==SYN_REPORT) {
                    sequence.dropped(e.device);unsigned char pressed[kBits]{};
                    if(ioctl(devices[e.device].fd,EVIOCGKEY(sizeof pressed),pressed)>=0)
                        for(int code:{KEY_POWER,KEY_VOLUMEUP,KEY_VOLUMEDOWN})if(hasBit(pressed,code)) {
                            int index=Sequence::keyIndex(code);sequence.held[e.device]|=1U<<index;++sequence.counts[index];
                        }
                    devices[e.device].syncLost=false;
                }
                continue;
            }
            if(e.event.type!=EV_KEY)continue;
            if(sequence.input(e.device,e.event.code,e.event.value,e.time)) {
                result=runRecovery(directory,boot)?0:23;stopped=1;break;
            }
        }
    }
    for(Device& device:devices)if(device.fd>=0)close(device.fd);
    removePid(directory,boot);close(lock);close(directory);return result;
}
bool selfTest() {
    // Five short Volume Up presses: only the fifth release fires.
    Sequence s;Tick t=100;for(int n=1;n<=Sequence::kPresses;++n) {
        if(s.input(0,KEY_VOLUMEUP,1,t))return false;
        bool fire=s.input(0,KEY_VOLUMEUP,0,t+50);if(fire!=(n==Sequence::kPresses))return false;t+=200;
    }return s.fired;
}
}
int main(int argc,char** argv) {
    if(argc==2&&!strcmp(argv[1],"--self-test")) {bool ok=selfTest();puts(ok?"sequence_self_test=ok":"sequence_self_test=failed");return ok?0:1;}
    if(argc==2&&!strcmp(argv[1],"--probe")) {
        c17rescue::ProbeCapabilities probe=probeCapabilities();
        printf("boot_window=%d\ninput_devices=%u\nusable_clock_devices=%u\nkey_coverage=%u\ndisabled=%d\n",
                bootRunning()?1:0,probe.readable,probe.usable,probe.coverage,disabled()?1:0);return 0;
    }
    if(argc>2||(argc==2&&strcmp(argv[1],"--watch")))return 2;
    (void)kModule;(void)kData;return watch();
}
