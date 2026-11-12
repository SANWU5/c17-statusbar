package android.os;
public final class ParcelFileDescriptor implements java.io.Closeable,Parcelable {
    public static final int MODE_READ_ONLY=0x10000000,MODE_WRITE_ONLY=0x20000000,MODE_TRUNCATE=0x04000000;
    private java.io.InputStream input;private java.io.OutputStream output;private java.io.RandomAccessFile writable;
    private ParcelFileDescriptor(){}
    public static ParcelFileDescriptor open(java.io.File file,int mode)throws java.io.FileNotFoundException{
        ParcelFileDescriptor descriptor=new ParcelFileDescriptor();
        if((mode&MODE_WRITE_ONLY)!=0)descriptor.output=new java.io.FileOutputStream(file,false);
        else descriptor.input=new java.io.FileInputStream(file);return descriptor;
    }
    public static ParcelFileDescriptor[] createPipe()throws java.io.IOException{
        java.io.PipedInputStream input=new java.io.PipedInputStream(65536);
        ParcelFileDescriptor read=new ParcelFileDescriptor(),write=new ParcelFileDescriptor();
        read.input=input;write.output=new java.io.PipedOutputStream(input);return new ParcelFileDescriptor[]{read,write};
    }
    public static ParcelFileDescriptor openReadWriteForCheck(java.io.File file)throws java.io.IOException{
        ParcelFileDescriptor descriptor=new ParcelFileDescriptor();
        descriptor.writable=new java.io.RandomAccessFile(file,"rw");
        descriptor.input=new java.io.FileInputStream(file);
        descriptor.output=new java.io.FileOutputStream(descriptor.writable.getFD());
        return descriptor;
    }
    public java.io.FileDescriptor getFileDescriptor(){try{
        if(output instanceof java.io.FileOutputStream)return((java.io.FileOutputStream)output).getFD();
        if(input instanceof java.io.FileInputStream)return((java.io.FileInputStream)input).getFD();
        return new java.io.FileDescriptor();
    }catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
    @Override public void close()throws java.io.IOException{
        if(input!=null)input.close();if(output!=null)output.close();if(writable!=null)writable.close();
    }
    @Override public int describeContents(){return Parcelable.CONTENTS_FILE_DESCRIPTOR;}
    @Override public void writeToParcel(Parcel destination,int flags){throw new UnsupportedOperationException("Parcel serialization is not emulated");}
    public static final class AutoCloseInputStream extends java.io.FilterInputStream{
        private final ParcelFileDescriptor descriptor;
        public AutoCloseInputStream(ParcelFileDescriptor descriptor){super(descriptor.input);this.descriptor=descriptor;}
        @Override public void close()throws java.io.IOException{descriptor.close();}
    }
    public static final class AutoCloseOutputStream extends java.io.FilterOutputStream{
        private final ParcelFileDescriptor descriptor;
        public AutoCloseOutputStream(ParcelFileDescriptor descriptor){super(descriptor.output);this.descriptor=descriptor;}
        @Override public void close()throws java.io.IOException{descriptor.close();}
    }
}
