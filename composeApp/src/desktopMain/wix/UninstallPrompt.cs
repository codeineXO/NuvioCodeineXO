using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace Nuvio.Installer
{
    static class UninstallPrompt
    {
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern int MessageBoxW(IntPtr hWnd, string lpText, string lpCaption, uint uType);

        [DllImport("user32.dll")]
        static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        static extern bool BringWindowToTop(IntPtr hWnd);

        [DllImport("user32.dll")]
        static extern bool IsWindowVisible(IntPtr hWnd);

        [DllImport("user32.dll")]
        static extern bool IsIconic(IntPtr hWnd);

        [DllImport("user32.dll")]
        static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        static extern int GetWindowTextW(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        static extern int GetClassNameW(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);

        delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

        [DllImport("user32.dll")]
        static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

        [DllImport("user32.dll")]
        static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);

        [DllImport("kernel32.dll")]
        static extern uint GetCurrentThreadId();

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern IntPtr CreateToolhelp32Snapshot(uint dwFlags, uint th32ProcessID);

        [DllImport("kernel32.dll", CharSet = CharSet.Auto)]
        static extern bool Process32First(IntPtr hSnapshot, ref PROCESSENTRY32 lppe);

        [DllImport("kernel32.dll", CharSet = CharSet.Auto)]
        static extern bool Process32Next(IntPtr hSnapshot, ref PROCESSENTRY32 lppe);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool CloseHandle(IntPtr hObject);

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        struct PROCESSENTRY32
        {
            public uint dwSize;
            public uint cntUsage;
            public uint th32ProcessID;
            public IntPtr th32DefaultHeapID;
            public uint th32ModuleID;
            public uint cntThreads;
            public uint th32ParentProcessID;
            public int pcPriClassBase;
            public uint dwFlags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)]
            public string szExeFile;
        }

        const uint TH32CS_SNAPPROCESS = 0x00000002;

        const uint MB_YESNO = 0x00000004;
        const uint MB_ICONQUESTION = 0x00000020;
        const uint MB_DEFBUTTON2 = 0x00000100;
        const uint MB_SYSTEMMODAL = 0x00001000;
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
                    !string.IsNullOrEmpty(roamingAppData) ? Path.Combine(roamingAppData, "NuvioCodeineXO") : null
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
                    "Do you want to delete your personal application data (settings, watch history, profiles, and cache) for NuvioCodeineXO from LocalAppData and Roaming?\n\n" +
                    "• Click 'Yes' to permanently delete your NuvioCodeineXO personal data and settings.\n" +
                    "• Click 'No' to preserve your settings in case you reinstall later.";

                string promptTitle = "Uninstall NuvioCodeineXO - Personal Data";

                uint parentPid = GetParentProcessId();
                IntPtr ownerHwnd = FindOwnerWindow(parentPid);

                if (ownerHwnd != IntPtr.Zero)
                {
                    try
                    {
                        uint currentThreadId = GetCurrentThreadId();
                        uint dummyPid;
                        uint targetThreadId = GetWindowThreadProcessId(ownerHwnd, out dummyPid);
                        if (targetThreadId != 0 && targetThreadId != currentThreadId)
                        {
                            AttachThreadInput(currentThreadId, targetThreadId, true);
                            SetForegroundWindow(ownerHwnd);
                            BringWindowToTop(ownerHwnd);
                            AttachThreadInput(currentThreadId, targetThreadId, false);
                        }
                        else
                        {
                            SetForegroundWindow(ownerHwnd);
                            BringWindowToTop(ownerHwnd);
                        }
                    }
                    catch { }
                }

                uint flags = MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2 | MB_SETFOREGROUND | MB_TOPMOST;
                if (ownerHwnd == IntPtr.Zero)
                {
                    flags |= MB_SYSTEMMODAL;
                }

                int result = MessageBoxW(ownerHwnd, promptText, promptTitle, flags);

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

        static uint GetParentProcessId()
        {
            uint parentPid = 0;
            try
            {
                int currentPid = Process.GetCurrentProcess().Id;
                IntPtr snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
                if (snapshot != IntPtr.Zero && snapshot != new IntPtr(-1))
                {
                    try
                    {
                        PROCESSENTRY32 entry = new PROCESSENTRY32();
                        entry.dwSize = (uint)Marshal.SizeOf(typeof(PROCESSENTRY32));
                        if (Process32First(snapshot, ref entry))
                        {
                            do
                            {
                                if (entry.th32ProcessID == (uint)currentPid)
                                {
                                    parentPid = entry.th32ParentProcessID;
                                    break;
                                }
                            } while (Process32Next(snapshot, ref entry));
                        }
                    }
                    finally
                    {
                        CloseHandle(snapshot);
                    }
                }
            }
            catch { }
            return parentPid;
        }

        static IntPtr FindOwnerWindow(uint parentPid)
        {
            IntPtr bestHwnd = IntPtr.Zero;
            int bestScore = 0;

            try
            {
                IntPtr fg = GetForegroundWindow();
                if (fg != IntPtr.Zero && IsWindowVisible(fg) && !IsIconic(fg))
                {
                    uint fgPid = 0;
                    GetWindowThreadProcessId(fg, out fgPid);
                    string fgTitle = GetWindowTitle(fg);

                    if (parentPid != 0 && fgPid == parentPid)
                    {
                        bestHwnd = fg;
                        bestScore = 100;
                    }
                    else if (IsMsiexecProcess(fgPid))
                    {
                        bestHwnd = fg;
                        bestScore = 80;
                    }
                    else if (fgTitle.IndexOf("Nuvio", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        bestHwnd = fg;
                        bestScore = 70;
                    }
                }

                if (bestScore >= 95)
                {
                    return bestHwnd;
                }

                EnumWindows(delegate(IntPtr hWnd, IntPtr lParam)
                {
                    try
                    {
                        if (!IsWindowVisible(hWnd) || IsIconic(hWnd))
                            return true;

                        uint pid = 0;
                        GetWindowThreadProcessId(hWnd, out pid);
                        string title = GetWindowTitle(hWnd);
                        string className = GetWindowClass(hWnd);

                        int score = 0;
                        bool isParent = (parentPid != 0 && pid == parentPid);
                        bool isMsiProc = IsMsiexecProcess(pid);
                        bool hasNuvio = title.IndexOf("Nuvio", StringComparison.OrdinalIgnoreCase) >= 0;
                        bool hasUninstall = title.IndexOf("Uninstall", StringComparison.OrdinalIgnoreCase) >= 0 ||
                                            title.IndexOf("Installer", StringComparison.OrdinalIgnoreCase) >= 0;
                        bool isMsiClass = className == "MsiDialogCloseClass" || className == "#32770";

                        if (isParent)
                        {
                            if (hasNuvio) score = 100;
                            else if (hasUninstall || isMsiClass) score = 95;
                            else if (!string.IsNullOrEmpty(title)) score = 85;
                            else score = 75;
                        }
                        else if (isMsiProc)
                        {
                            if (hasNuvio) score = 90;
                            else if (hasUninstall || isMsiClass) score = 80;
                            else if (!string.IsNullOrEmpty(title)) score = 65;
                        }
                        else if (hasNuvio && !string.IsNullOrEmpty(title))
                        {
                            score = 60;
                        }

                        if (score > bestScore)
                        {
                            bestScore = score;
                            bestHwnd = hWnd;
                        }
                    }
                    catch { }

                    return true;
                }, IntPtr.Zero);
            }
            catch { }

            return bestHwnd;
        }

        static bool IsMsiexecProcess(uint pid)
        {
            if (pid == 0) return false;
            try
            {
                Process proc = Process.GetProcessById((int)pid);
                return string.Equals(proc.ProcessName, "msiexec", StringComparison.OrdinalIgnoreCase);
            }
            catch
            {
                return false;
            }
        }

        static string GetWindowTitle(IntPtr hWnd)
        {
            try
            {
                StringBuilder sb = new StringBuilder(512);
                int len = GetWindowTextW(hWnd, sb, sb.Capacity);
                return len > 0 ? sb.ToString() : string.Empty;
            }
            catch
            {
                return string.Empty;
            }
        }

        static string GetWindowClass(IntPtr hWnd)
        {
            try
            {
                StringBuilder sb = new StringBuilder(256);
                int len = GetClassNameW(hWnd, sb, sb.Capacity);
                return len > 0 ? sb.ToString() : string.Empty;
            }
            catch
            {
                return string.Empty;
            }
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
