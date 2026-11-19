using System;
using System.IO;
using System.Diagnostics;
using System.Text;

class JPackageWrapper
{
    static bool HasJPackage(string javaHome)
    {
        return !string.IsNullOrEmpty(javaHome) && File.Exists(Path.Combine(javaHome, @"bin\jpackage.exe"));
    }

    static int Main(string[] args)
    {
        try
        {
            string currentDir = AppDomain.CurrentDomain.BaseDirectory;
            // Traverse up to find root of NuvioDesktop repo
            string repoDir = Path.GetFullPath(Path.Combine(currentDir, @"..\..\.."));
            string wixSrc = Path.Combine(repoDir, @"composeApp\src\desktopMain\wix");
            string resourcesDir = Path.Combine(repoDir, @"composeApp\build\compose\tmp\resources");

            if (Directory.Exists(wixSrc))
            {
                Directory.CreateDirectory(resourcesDir);
                foreach (string file in Directory.GetFiles(wixSrc))
                {
                    if (file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase)) continue;
                    string dest = Path.Combine(resourcesDir, Path.GetFileName(file));
                    File.Copy(file, dest, true);
                }

                string promptExe = Path.Combine(wixSrc, "UninstallPrompt.exe");
                string shortcutHelperExe = Path.Combine(wixSrc, "DesktopShortcutHelper.exe");
                string overridesPath = Path.Combine(resourcesDir, "overrides.wxi");
                string overridesContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<Include>\r\n  <?define UninstallPromptSource=\"" + promptExe + "\" ?>\r\n  <?define DesktopShortcutHelperSource=\"" + shortcutHelperExe + "\" ?>\r\n</Include>\r\n";
                File.WriteAllText(overridesPath, overridesContent);

                Console.WriteLine("[JPackageWrapper] Successfully staged custom WiX templates to " + resourcesDir);
            }

            // Prefer the JDK Gradle is running on (written by the packageMsi task), then JAVA_HOME.
            string realJavaHome = null;
            string javaHomeFile = Path.GetFullPath(Path.Combine(currentDir, @"..\real-java-home.txt"));
            if (File.Exists(javaHomeFile))
            {
                string fromFile = File.ReadAllText(javaHomeFile).Trim();
                if (HasJPackage(fromFile)) realJavaHome = fromFile;
            }
            if (realJavaHome == null)
            {
                string fromEnv = Environment.GetEnvironmentVariable("JAVA_HOME");
                if (HasJPackage(fromEnv)) realJavaHome = fromEnv;
            }
            if (realJavaHome == null)
            {
                Console.Error.WriteLine("[JPackageWrapper Error] Cannot find a JDK with jpackage.exe. Checked " + javaHomeFile + " and JAVA_HOME.");
                return 1;
            }

            string realJPackage = Path.Combine(realJavaHome, @"bin\jpackage.exe");
            Console.WriteLine("[JPackageWrapper] Using jpackage from " + realJavaHome);

            StringBuilder sb = new StringBuilder();
            bool hasVerbose = false;
            foreach (string arg in args)
            {
                if (arg == "--verbose") hasVerbose = true;
                if (sb.Length > 0) sb.Append(" ");
                if (arg.Contains(" ") && !arg.StartsWith("\""))
                    sb.Append("\"").Append(arg).Append("\"");
                else
                    sb.Append(arg);
            }
            if (!hasVerbose)
            {
                sb.Append(" --verbose");
            }
            string tempBase = Path.Combine(repoDir, @"build\jpackage-temp");
            try
            {
                if (Directory.Exists(tempBase))
                {
                    Directory.Delete(tempBase, true);
                }
            }
            catch { }
            string tempDir = Path.Combine(tempBase, Guid.NewGuid().ToString("N"));
            sb.Append(" --temp \"").Append(tempDir).Append("\"");

            var psi = new ProcessStartInfo
            {
                FileName = realJPackage,
                Arguments = sb.ToString(),
                UseShellExecute = false
            };

            string wixBin = Path.Combine(repoDir, @"build\wix311");
            if (Directory.Exists(wixBin))
            {
                string path = Environment.GetEnvironmentVariable("PATH") ?? "";
                if (path.IndexOf(wixBin, StringComparison.OrdinalIgnoreCase) < 0)
                {
                    Environment.SetEnvironmentVariable("PATH", wixBin + ";" + path);
                }
                Environment.SetEnvironmentVariable("WIX", wixBin);
            }

            using (var proc = Process.Start(psi))
            {
                proc.WaitForExit();
                return proc.ExitCode;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[JPackageWrapper Error] " + ex.Message);
            return 1;
        }
    }

}
