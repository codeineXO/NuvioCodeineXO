using System;
using System.IO;
using System.Runtime.InteropServices;

namespace Nuvio.Installer
{
    static class UninstallPrompt
    {
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern int MessageBoxW(IntPtr hWnd, string lpText, string lpCaption, uint uType);

        const uint MB_YESNO = 0x00000004;
        const uint MB_ICONQUESTION = 0x00000020;
        const uint MB_DEFBUTTON2 = 0x00000100;
        const uint MB_SETFOREGROUND = 0x00010000;
        const uint MB_TOPMOST = 0x00040000;
        const int IDYES = 6;

        [STAThread]
        static int Main(string[] args)
        {
            try
            {
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                if (string.IsNullOrEmpty(localAppData))
                {
                    localAppData = Environment.GetEnvironmentVariable("LOCALAPPDATA");
                }

                string roamingAppData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                if (string.IsNullOrEmpty(roamingAppData))
                {
                    roamingAppData = Environment.GetEnvironmentVariable("APPDATA");
                }

                if (string.IsNullOrEmpty(localAppData) && string.IsNullOrEmpty(roamingAppData))
                {
                    return 0;
                }

                string[] targetDirs = new string[]
                {
                    !string.IsNullOrEmpty(localAppData) ? Path.Combine(localAppData, "NuvioCodeineXO") : null,
                    !string.IsNullOrEmpty(roamingAppData) ? Path.Combine(roamingAppData, "NuvioCodeineXO") : null,
                    !string.IsNullOrEmpty(localAppData) ? Path.Combine(localAppData, "Nuvio") : null,
                    !string.IsNullOrEmpty(roamingAppData) ? Path.Combine(roamingAppData, "Nuvio") : null
                };

                bool anyExists = false;
                foreach (string dir in targetDirs)
                {
                    if (dir != null && Directory.Exists(dir))
                    {
                        anyExists = true;
                        break;
                    }
                }

                if (!anyExists)
                {
                    // No user data exists, exit quietly
                    return 0;
                }

                string promptText = 
                    "Do you want to delete your personal application data (settings, watch history, profiles, and cache) from LocalAppData and Roaming?\n\n" +
                    "• Click 'Yes' to permanently delete all personal data and settings.\n" +
                    "• Click 'No' to preserve your settings in case you reinstall later.";

                string promptTitle = "Uninstall NuvioCodeineXO - Personal Data";

                uint flags = MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2 | MB_SETFOREGROUND | MB_TOPMOST;

                int result = MessageBoxW(IntPtr.Zero, promptText, promptTitle, flags);

                if (result == IDYES)
                {
                    foreach (string dir in targetDirs)
                    {
                        if (dir != null && Directory.Exists(dir))
                        {
                            DeleteDirectorySafe(dir);
                        }
                    }
                }
            }
            catch
            {
                // Silently catch all exceptions so uninstaller never breaks
            }

            return 0;
        }

        static void DeleteDirectorySafe(string path)
        {
            try
            {
                var dir = new DirectoryInfo(path);
                if (!dir.Exists) return;

                foreach (var file in dir.GetFiles("*", SearchOption.AllDirectories))
                {
                    try
                    {
                        file.Attributes = FileAttributes.Normal;
                    }
                    catch { }
                }

                dir.Delete(true);
            }
            catch
            {
                try
                {
                    Directory.Delete(path, true);
                }
                catch { }
            }
        }
    }
}
