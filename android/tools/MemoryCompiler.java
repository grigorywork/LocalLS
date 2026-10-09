import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Reads approved classpath files directly when Windows real-path lookup is unavailable. */
class MemoryCompiler {
 static class Binary extends SimpleJavaFileObject {
  final String name; final byte[] bytes;
  Binary(String name, byte[] bytes) { super(URI.create("mem:///"+name.replace('.','/')+".class"),Kind.CLASS);this.name=name;this.bytes=bytes; }
  public InputStream openInputStream(){ return new ByteArrayInputStream(bytes); }
 }
 static class Manager extends ForwardingJavaFileManager<StandardJavaFileManager> {
  final Map<String,Binary> classes=new LinkedHashMap<>();
  Manager(StandardJavaFileManager base,String cp) throws Exception {
   super(base);
   for(String value:cp.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
    Path p=Path.of(value);
    if(Files.isDirectory(p)) {
     try(var paths=Files.walk(p)){ for(Path c:paths.filter(x->x.toString().endsWith(".class")).toList()) add(p.relativize(c).toString().replace('\\','/'),Files.readAllBytes(c)); }
    } else {
     try(ZipFile z=new ZipFile(p.toFile())){ var entries=z.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(e.getName().endsWith(".class")&&!e.getName().startsWith("META-INF/")){try(var in=z.getInputStream(e)){add(e.getName(),in.readAllBytes());}}} }
    }
   }
  }
  void add(String path,byte[] bytes){String name=path.substring(0,path.length()-6).replace('/','.');classes.putIfAbsent(name,new Binary(name,bytes));}
  public String inferBinaryName(Location l,JavaFileObject f){return f instanceof Binary?((Binary)f).name:super.inferBinaryName(l,f);}
  public Iterable<JavaFileObject> list(Location l,String pkg,Set<JavaFileObject.Kind> kinds,boolean recurse)throws IOException {
   if(l==StandardLocation.CLASS_PATH){List<JavaFileObject> out=new ArrayList<>();if(kinds.contains(JavaFileObject.Kind.CLASS)){String prefix=pkg.isEmpty()?"":pkg+".";for(Binary b:classes.values()){if(b.name.startsWith(prefix)&&(recurse||b.name.substring(prefix.length()).indexOf('.')<0))out.add(b);}}return out;}
   return super.list(l,pkg,kinds,recurse);
  }
  public JavaFileObject getJavaFileForInput(Location l,String name,JavaFileObject.Kind kind)throws IOException {
   if(l==StandardLocation.CLASS_PATH&&kind==JavaFileObject.Kind.CLASS)return classes.get(name);
   return super.getJavaFileForInput(l,name,kind);
  }
 }
 public static void main(String[] args)throws Exception {
  var compiler=ToolProvider.getSystemJavaCompiler();
  try(var base=compiler.getStandardFileManager(null,null,java.nio.charset.StandardCharsets.UTF_8);var fm=new Manager(base,args[0])){
   List<String> options=List.of("-encoding","UTF-8","-proc:none","-source","17","-target","17","-d",args[1]);
   boolean ok=compiler.getTask(null,fm,null,options,null,base.getJavaFileObjectsFromStrings(Arrays.asList(args).subList(2,args.length))).call();
   if(!ok)System.exit(1);System.out.println("COMPILE VERIFIED: "+(args.length-2)+" sources");
  }
 }
}
