using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

namespace Nuvio.Installer
{
    [StructLayout(LayoutKind.Sequential)]
    public struct POINT
    {
        public int x;
        public int y;
    }

    [ComImport]
    [Guid("000214E6-0000-0000-C000-000000000046")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IShellFolder
    {
        void ParseDisplayName(IntPtr hwnd, IntPtr pbc, [MarshalAs(UnmanagedType.LPWStr)] string pszDisplayName, out uint pchEaten, out IntPtr ppidl, ref uint pdwAttributes);
        void EnumObjects(IntPtr hwnd, uint grfFlags, out IntPtr ppenumIDList);
        void BindToObject(IntPtr pidl, IntPtr pbc, [In] ref Guid riid, out IntPtr ppv);
        void BindToStorage(IntPtr pidl, IntPtr pbc, [In] ref Guid riid, out IntPtr ppv);
        void CompareIDs(IntPtr lParam, IntPtr pidl1, IntPtr pidl2);
        void CreateViewObject(IntPtr hwndOwner, [In] ref Guid riid, out IntPtr ppv);
        void GetAttributesOf(uint cidl, [MarshalAs(UnmanagedType.LPArray)] IntPtr[] apidl, ref uint rgfInOut);
        void GetUIObjectOf(IntPtr hwndOwner, uint cidl, [MarshalAs(UnmanagedType.LPArray)] IntPtr[] apidl, [In] ref Guid riid, IntPtr rgfReserved, out IntPtr ppv);
        [PreserveSig]
        int GetDisplayNameOf(IntPtr pidl, uint uFlags, out STRRET pName);
        void SetNameOf(IntPtr hwnd, IntPtr pidl, [MarshalAs(UnmanagedType.LPWStr)] string pszName, uint uFlags, out IntPtr ppidlOut);
    }

    [StructLayout(LayoutKind.Explicit, Size = 520)]
    public struct STRRET
    {
        [FieldOffset(0)]
        public uint uType;
        [FieldOffset(4)]
        public IntPtr pOleStr;
        [FieldOffset(4)]
        public uint uOffset;
        [FieldOffset(4)]
        public IntPtr cStr;
    }

    [ComImport]
    [Guid("cde725b0-ccc9-4519-917e-325d72fab4ce")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IFolderView
    {
        void GetCurrentViewMode(out uint pViewMode);
        void SetCurrentViewMode(uint ViewMode);
        void GetFolder([In] ref Guid riid, [MarshalAs(UnmanagedType.IUnknown)] out object ppv);
        void Item(int iItemIndex, out IntPtr ppidl);
        void ItemCount(uint uFlags, out int pcItems);
        void Items(uint uFlags, [In] ref Guid riid, out IntPtr ppv);
        void GetSelectionMarkedItem(out int piItem);
        void GetFocusedItem(out int piItem);
        [PreserveSig]
        int GetItemPosition(IntPtr pidl, out POINT ppt);
        void GetSpacing(ref POINT ppt);
        void GetDefaultSpacing(ref POINT ppt);
        void GetAutoArrange();
        void SelectItem(int iItem, uint dwFlags);
        [PreserveSig]
        int SelectAndPositionItems(uint cidl, [MarshalAs(UnmanagedType.LPArray)] IntPtr[] apidl, [MarshalAs(UnmanagedType.LPArray)] POINT[] apt, uint dwFlags);
    }

    [ComImport]
    [Guid("000214E2-0000-0000-C000-000000000046")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IShellBrowser
    {
        void GetWindow(out IntPtr phwnd);
        void ContextSensitiveHelp(bool fEnterMode);
        void InsertMenusSB(IntPtr hmenuShared, IntPtr lpMenuWidths);
        void SetMenuSB(IntPtr hmenuShared, IntPtr holemenuRes, IntPtr hwndActiveObject);
        void RemoveMenusSB(IntPtr hmenuShared);
        void SetStatusTextSB([MarshalAs(UnmanagedType.LPWStr)] string pszStatusText);
        void EnableModelessSB(bool fEnable);
        void TranslateAcceleratorSB(IntPtr pmsg, ushort wID);
        void BrowseObject(IntPtr pidl, uint wFlags);
        void GetViewStateStream(uint grfMode, out IntPtr ppStrm);
        void GetControlWindow(uint id, out IntPtr phwnd);
        void SendControlMsg(uint id, uint uMsg, IntPtr wParam, IntPtr lParam, out IntPtr pret);
        void QueryActiveShellView([MarshalAs(UnmanagedType.IUnknown)] out object ppshv);
    }

    [ComImport]
    [Guid("6d5140c1-7436-11ce-8034-00aa006009fa")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IServiceProvider
    {
        [PreserveSig]
        int QueryService([In] ref Guid guidService, [In] ref Guid riid, [MarshalAs(UnmanagedType.IUnknown)] out object ppvObject);
    }

    [ComImport]
    [Guid("85CB6900-4D95-11CF-960C-0080C7F4EE85")]
    [InterfaceType(ComInterfaceType.InterfaceIsIDispatch)]
    public interface IShellWindows
    {
        [return: MarshalAs(UnmanagedType.IDispatch)]
        object FindWindowSW([In] ref object pvarloc, [In] ref object pvarlocRoot, int swClass, out int pHWND, int swflags);
    }

    static class DesktopShortcutHelper
    {
        [DllImport("shlwapi.dll", EntryPoint = "StrRetToBufW", ExactSpelling = true)]
        static extern int StrRetToBuf(ref STRRET pstr, IntPtr pidl, [MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszBuf, uint cchBuf);

        [DllImport("ole32.dll")]
        static extern void CoTaskMemFree(IntPtr pv);

        private static string PosFilePath
        {
            get
            {
                string tempDir = Path.GetTempPath();
                return Path.Combine(tempDir, "nuvio_desktop_shortcut.pos");
            }
        }

        private static bool GetDesktopFolderView(out IFolderView fv, out IShellFolder folder)
        {
            fv = null;
            folder = null;
            try
            {
                Type shellWindowsType = Type.GetTypeFromCLSID(new Guid("9BA05972-F6A8-11CF-A442-00A0C90A8F39"));
                IShellWindows shellWindows = (IShellWindows)Activator.CreateInstance(shellWindowsType);

                object vEmpty = "";
                int hwnd;
                object disp = shellWindows.FindWindowSW(ref vEmpty, ref vEmpty, 8 /* SWC_DESKTOP */, out hwnd, 1 /* SWFO_NEEDDISPATCH */);
                if (disp == null) return false;

                IServiceProvider sp = (IServiceProvider)disp;
                Guid sidTopLevelBrowser = new Guid("4C96BE40-915C-11CF-99D3-00AA004AE837");
                Guid iidShellBrowser = new Guid("000214E2-0000-0000-C000-000000000046");
                object sbObj;
                int hr = sp.QueryService(ref sidTopLevelBrowser, ref iidShellBrowser, out sbObj);
                if (hr != 0 || sbObj == null) return false;

                IShellBrowser sb = (IShellBrowser)sbObj;
                object svObj;
                sb.QueryActiveShellView(out svObj);
                if (svObj == null) return false;

                fv = (IFolderView)svObj;
                Guid iidShellFolder = new Guid("000214E6-0000-0000-C000-000000000046");
                object folderObj;
                fv.GetFolder(ref iidShellFolder, out folderObj);
                folder = (IShellFolder)folderObj;
                return fv != null && folder != null;
            }
            catch
            {
                return false;
            }
        }

        private static bool IsTargetItem(string name, bool includeLegacy)
        {
            if (string.IsNullOrEmpty(name)) return false;
            if (name.Equals("NuvioCodeineXO", StringComparison.OrdinalIgnoreCase) ||
                name.Equals("NuvioCodeineXO.lnk", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
            if (includeLegacy && (name.Equals("Nuvio", StringComparison.OrdinalIgnoreCase) ||
                                  name.Equals("Nuvio.lnk", StringComparison.OrdinalIgnoreCase)))
            {
                return true;
            }
            return false;
        }

        private static void SavePosition()
        {
            try
            {
                IFolderView fv;
                IShellFolder folder;
                if (!GetDesktopFolderView(out fv, out folder))
                {
                    File.WriteAllText(PosFilePath, "NOT_FOUND");
                    return;
                }

                int count;
                fv.ItemCount(0, out count);
                for (int i = 0; i < count; i++)
                {
                    IntPtr pidl;
                    fv.Item(i, out pidl);
                    if (pidl != IntPtr.Zero)
                    {
                        try
                        {
                            STRRET strret;
                            folder.GetDisplayNameOf(pidl, 0 /* SHGDN_NORMAL */, out strret);
                            StringBuilder name = new StringBuilder(260);
                            StrRetToBuf(ref strret, pidl, name, (uint)name.Capacity);

                            if (IsTargetItem(name.ToString(), true))
                            {
                                POINT pt;
                                fv.GetItemPosition(pidl, out pt);
                                File.WriteAllText(PosFilePath, pt.x + "," + pt.y);
                                return;
                            }
                        }
                        finally
                        {
                            CoTaskMemFree(pidl);
                        }
                    }
                }

                File.WriteAllText(PosFilePath, "NOT_FOUND");
            }
            catch
            {
                // Silently swallow errors to avoid interrupting install
            }
        }

        private static void RestorePosition()
        {
            try
            {
                if (!File.Exists(PosFilePath)) return;

                string content = File.ReadAllText(PosFilePath).Trim();
                if (string.IsNullOrEmpty(content)) return;

                if (content.Equals("NOT_FOUND", StringComparison.OrdinalIgnoreCase))
                {
                    // User did not have a desktop shortcut before update.
                    // If the installer recreated one, remove it to respect user's desktop preference.
                    DeleteShortcutIfExists();
                    try { File.Delete(PosFilePath); } catch { }
                    return;
                }

                string[] parts = content.Split(',');
                if (parts.Length != 2) return;

                int x, y;
                if (!int.TryParse(parts[0], out x) || !int.TryParse(parts[1], out y)) return;

                POINT targetPt;
                targetPt.x = x;
                targetPt.y = y;

                // Explorer may take a brief moment to register the newly installed shortcut.
                for (int attempt = 0; attempt < 15; attempt++)
                {
                    IFolderView fv;
                    IShellFolder folder;
                    if (GetDesktopFolderView(out fv, out folder))
                    {
                        int count;
                        fv.ItemCount(0, out count);
                        for (int i = 0; i < count; i++)
                        {
                            IntPtr pidl;
                            fv.Item(i, out pidl);
                            if (pidl != IntPtr.Zero)
                            {
                                try
                                {
                                    STRRET strret;
                                    folder.GetDisplayNameOf(pidl, 0 /* SHGDN_NORMAL */, out strret);
                                    StringBuilder name = new StringBuilder(260);
                                    StrRetToBuf(ref strret, pidl, name, (uint)name.Capacity);

                                    if (IsTargetItem(name.ToString(), false))
                                    {
                                        fv.SelectAndPositionItems(1, new IntPtr[] { pidl }, new POINT[] { targetPt }, 0x80 /* SVSI_POSITIONITEM */);
                                        try { File.Delete(PosFilePath); } catch { }
                                        return;
                                    }
                                }
                                finally
                                {
                                    CoTaskMemFree(pidl);
                                }
                            }
                        }
                    }
                    Thread.Sleep(200);
                }

                try { File.Delete(PosFilePath); } catch { }
            }
            catch
            {
                // Silently swallow errors to avoid interrupting install
            }
        }

        private static void DeleteShortcutIfExists()
        {
            try
            {
                string userDesktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                string publicDesktop = Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory);

                string[] paths = new string[]
                {
                    Path.Combine(userDesktop, "NuvioCodeineXO.lnk"),
                    Path.Combine(publicDesktop, "NuvioCodeineXO.lnk")
                };

                foreach (string path in paths)
                {
                    if (File.Exists(path))
                    {
                        File.Delete(path);
                    }
                }
            }
            catch { }
        }

        [STAThread]
        static int Main(string[] args)
        {
            if (args.Length > 0 && args[0].Equals("restore", StringComparison.OrdinalIgnoreCase))
            {
                RestorePosition();
            }
            else
            {
                SavePosition();
            }
            return 0;
        }
    }
}
