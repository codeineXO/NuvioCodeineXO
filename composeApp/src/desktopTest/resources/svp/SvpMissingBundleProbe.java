import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
public class SvpMissingBundleProbe {
  static class MaskedLoader extends URLClassLoader {
    volatile boolean mask = true;
    MaskedLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
    public InputStream getResourceAsStream(String name) {
      if (mask && name.equals("svp-runtime/windows-x64/python312.dll")) return null;
      return super.getResourceAsStream(name);
    }
  }
  static Object singleton(Class<?> type) throws Exception {
    Field field = Arrays.stream(type.getDeclaredFields()).filter(f -> f.getType() == type && Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
    field.setAccessible(true); return field.get(null);
  }
  public static void main(String[] args) throws Exception {
    URL[] jars;
    try (var paths = Files.lines(Paths.get(args[0]))) {
      jars = paths.map(p -> {try {return URI.create(p).toURL();} catch(Exception e) {throw new RuntimeException(e);}}).toArray(URL[]::new);
    }
    try (MaskedLoader loader = new MaskedLoader(jars)) {
      Class<?> bridge = Class.forName("com.nuvio.app.features.player.desktop.NativePlayerBridge", true, loader);
      Object instance = singleton(bridge);
      bridge.getDeclaredMethod("ensureLoaded").invoke(instance);
      Method available = bridge.getDeclaredMethod("isSvpAvailable");
      if (Boolean.TRUE.equals(available.invoke(instance))) throw new AssertionError("Missing bundle reported available");
      System.out.println("Missing Python DLL: SVP unavailable; native playback bridge still loads");
      Class<?> installer = Class.forName("com.nuvio.app.features.player.SvpRuntimeInstaller", true, loader);
      Object service = singleton(installer);
      Method retry = installer.getDeclaredMethod("startDownload");
      loader.mask = false;
      long deadline = System.nanoTime() + 15_000_000_000L;
      while (!Boolean.TRUE.equals(available.invoke(instance)) && System.nanoTime() < deadline) {
        retry.invoke(service); Thread.sleep(100);
      }
      if (!Boolean.TRUE.equals(available.invoke(instance))) throw new AssertionError("Offline repair did not recover availability");
      System.out.println("Offline retry recovered after an earlier failed availability check");
    }
  }
}