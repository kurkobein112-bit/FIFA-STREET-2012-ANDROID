"""Exercise the actual Java XDVDFS parser and extractor with hostile images."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ.get("JAVA_HOME", str(ROOT / ".tools/jdk/jdk-17.0.20.1+1"))) / "bin"


class PublicIsoTest(unittest.TestCase):
    def test_parser_extraction_bounds_cycles_duplicates_and_cancel(self):
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            harness = work / "IsoTest.java"
            harness.write_text(r'''
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.restreet.android.preview.IsoExtractor;
public class IsoTest {
  static byte[] sample(String name) {
    byte[] data=new byte[0x18000];ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    byte[] magic="MICROSOFT*XBOX*MEDIA".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    System.arraycopy(magic,0,data,0x10000,20);System.arraycopy(magic,0,data,0x107ec,20);
    b.putInt(0x10014,40);b.putInt(0x10018,256);
    record(data,0,name,44,4,0);data[44*2048]='X';data[44*2048+1]='E';data[44*2048+2]='X';data[44*2048+3]='2';
    return data;
  }
  static void record(byte[] data,int at,String name,int sector,int size,int flags) {
    int p=40*2048+at;ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(p+4,sector);b.putInt(p+8,size);data[p+12]=(byte)flags;data[p+13]=(byte)name.length();
    System.arraycopy(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,data,p+14,name.length());
  }
  interface Checked {void run() throws Exception;}
  static void reject(Checked c) throws Exception {boolean bad=false;try{c.run();}catch(IOException e){bad=true;}assert bad;}
  static void inventory(Path iso,byte[] data) throws Exception {
    Files.write(iso,data);try(FileInputStream in=new FileInputStream(iso.toFile())){new IsoExtractor(in.getChannel()).inventory();}
  }
  public static void main(String[] args) throws Exception {
    Path root=Paths.get(args[0]),iso=root.resolve("input.iso");byte[] data=sample("default.xex");Files.write(iso,data);
    try(FileInputStream in=new FileInputStream(iso.toFile())) {
      IsoExtractor reader=new IsoExtractor(in.getChannel());List<IsoExtractor.Entry> entries=reader.inventory();
      assert entries.size()==1;assert entries.get(0).path.equals("default.xex");assert IsoExtractor.totalBytes(entries)==4;
      File out=root.resolve("output").toFile();reader.extract(entries,out,(done,total)->{assert done<=total;});
      assert Arrays.equals(Files.readAllBytes(out.toPath().resolve("default.xex")),new byte[]{'X','E','X','2'});
      reject(()->reader.extract(entries,out,(done,total)->{}));
      reject(()->reader.validateGame(entries));
      reject(()->reader.extract(entries,root.resolve("cancel").toFile(),(done,total)->{throw new IOException("cancel");}));
    }
    assert Arrays.equals(Files.readAllBytes(iso),data);
    for(String name:new String[]{"../bad","..","bad:name","folder/file","end.","end ","bad\\file"})reject(()->inventory(iso,sample(name)));
    byte[] bad=sample("file");ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putShort(40*2048,(short)8);
    record(bad,32,"other",44,4,0);ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putShort(40*2048+32,(short)8);
    reject(()->inventory(iso,bad));
    byte[] duplicate=sample("same");record(duplicate,32,"SAME",44,4,0);ByteBuffer.wrap(duplicate).order(ByteOrder.LITTLE_ENDIAN).putShort(40*2048,(short)8);
    reject(()->inventory(iso,duplicate));
    byte[] outside=sample("file");ByteBuffer.wrap(outside).order(ByteOrder.LITTLE_ENDIAN).putInt(40*2048+4,-1);
    reject(()->inventory(iso,outside));
    reject(()->inventory(iso,new byte[128]));
    if(args.length>1)try(FileInputStream real=new FileInputStream(args[1])){
      IsoExtractor reader=new IsoExtractor(real.getChannel());List<IsoExtractor.Entry> entries=reader.inventory();reader.validateGame(entries);
      System.out.println("Real ISO validated: "+entries.size()+" entries, "+IsoExtractor.totalBytes(entries)+" bytes");
    }
    System.out.println("Java ISO parser/extractor assertions passed");
  }
}
''', encoding="utf-8")
            source = ROOT / "android/src/org/restreet/android/preview/IsoExtractor.java"
            subprocess.run([str(JAVA / "javac.exe"), "-encoding", "UTF-8", "-d", str(work), str(source), str(harness)], check=True, capture_output=True)
            command = [str(JAVA / "java.exe"), "-ea", "-cp", str(work), "IsoTest", str(work)]
            iso = os.environ.get("FIFA_TEST_ISO")
            if iso:
                command.append(iso)
            result = subprocess.run(command, check=True, capture_output=True, text=True)
            self.assertIn("assertions passed", result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
