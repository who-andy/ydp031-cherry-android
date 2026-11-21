// Built with the Windows .NET Framework 4.x compiler; no separate runtime install.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;

internal static class CherryWindows
{
    const string Distro = "Ubuntu-24.04";
    static readonly string Folder = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
    static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

    internal sealed class UsbDevice {
        public string BusId { get; set; }
        public string InstanceId { get; set; }
        public string PersistedGuid { get; set; }
        public string ClientIPAddress { get; set; }
    }
    internal sealed class Inventory { public UsbDevice[] Devices { get; set; } }

    internal static UsbDevice Loader(string json) {
        var inventory = Json.Deserialize<Inventory>(json);
        if (inventory == null || inventory.Devices == null) throw new IOException("无法读取 usbipd 设备清单。");
        var found = inventory.Devices.Where(d => d != null && d.BusId != null &&
            Regex.IsMatch(d.InstanceId ?? "", @"^USB\\VID_2207&PID_330D(?:&[^\\]*)?\\", RegexOptions.IgnoreCase)).ToArray();
        if (found.Length > 1) throw new IOException("检测到多台 Rockchip 维护设备，请只保留目标词典笔。");
        if (found.Length == 0) return null;
        if (!Regex.IsMatch(found[0].BusId, @"^[1-9]\d*-[1-9]\d*$")) throw new IOException("USB 端口编号无效。");
        return found[0];
    }

    internal static string AdbSerial(string text) {
        var rows = text.Split(new[]{'\r','\n'}, StringSplitOptions.RemoveEmptyEntries)
            .Select(s => s.Trim()).Where(s => s.Length != 0 && !s.StartsWith("List of devices attached")).ToArray();
        if (rows.Length != 1) throw new IOException("请只连接一支词典笔。若列表为空，请查看 USB 调试开关、数据线及 Windows ADB 驱动。");
        var fields = Regex.Split(rows[0], @"\s+");
        if (fields.Length != 2 || fields[1] != "device")
            throw new IOException("ADB 尚未就绪。请在词典笔上允许这台电脑并重新打开安装器。");
        if (!Regex.IsMatch(fields[0], @"^[A-Za-z0-9]{16}$")) throw new IOException("ADB 序列号与此机型不匹配。");
        return fields[0];
    }

    // Windows CreateProcess accepts a single command line. Quote using CRT argv rules.
    internal static string Quote(string value) {
        var result=new StringBuilder();
        result.Append('"');
        int slashes=0;
        foreach(char c in value) {
            if (c=='\\') { slashes++; continue; }
            if (c=='"') result.Append('\\',slashes*2+1);
            else result.Append('\\',slashes);
            result.Append(c);
            slashes=0;
        }
        result.Append('\\',slashes*2);
        return result.Append('"').ToString();
    }

    static string ProgramPath(string name) {
        var folders = new List<string>();
        if (name == "wsl") folders.Add(Environment.SystemDirectory);
        if (name == "usbipd") folders.Add(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"usbipd-win"));
        foreach (string s in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';'))
            if (!String.IsNullOrWhiteSpace(s) && Path.IsPathRooted(s.Trim('"'))) folders.Add(s.Trim('"'));
        foreach (string dir in folders) {
            string file=Path.Combine(dir,name+".exe");
            if (File.Exists(file)) return file;
        }
        throw new IOException("找不到 " + name + "。请按 Windows 刷机说明准备工具，然后重新打开安装器。");
    }

    static string Run(string tool, bool capture, bool elevated, params string[] args) {
        var info=new ProcessStartInfo(ProgramPath(tool),String.Join(" ",args.Select(Quote))) {
            UseShellExecute=elevated, RedirectStandardOutput=capture, WorkingDirectory=Folder
        };
        if (elevated) info.Verb="runas";
        if (capture) info.StandardOutputEncoding=Encoding.UTF8;
        using (var p=Process.Start(info)) {
            string text=capture ? p.StandardOutput.ReadToEnd() : "";
            p.WaitForExit();
            if (p.ExitCode!=0) throw new IOException(tool+" 返回 "+p.ExitCode+"。请查看上方提示。");
            return text;
        }
    }

    static UsbDevice CurrentLoader() { return Loader(Run("usbipd",true,false,"state")); }

    static void Bridge() {
        var device=CurrentLoader();
        string serial="";
        if (device==null) {
            serial=AdbSerial(Run("adb",true,false,"devices"));
            Console.Error.WriteLine("正在将 "+serial+" 重启到 USB 维护模式。");
            Run("adb",true,false,"-s",serial,"reboot","bootloader");
            for(int i=0; i<45 && device==null; i++) { Thread.Sleep(1000); device=CurrentLoader(); }
            if (device==null) throw new IOException("Windows 未识别到词典笔的 Loader。已停止，尚未写入。");
        }
        string bus=device.BusId;
        if (device.PersistedGuid==null) {
            Console.Error.WriteLine("Windows 将请求管理员确认，以共享目标 USB 端口 "+bus+"。");
            Run("usbipd",false,true,"bind","--busid",bus);
            device=CurrentLoader();
            if (device==null || device.BusId!=bus || device.PersistedGuid==null)
                throw new IOException("USB 设备身份或共享状态变化，已停止。");
        }
        if (device.ClientIPAddress==null) {
            // Capture stdout so only the expected serial is passed to the Linux verifier.
            Run("usbipd",true,false,"attach","--wsl",Distro,"--busid",bus);
        }
        Console.WriteLine("CHERRY_SERIAL="+serial);
    }

    static void CheckBundle() {
        var map=Json.Deserialize<Dictionary<string,string>>(File.ReadAllText(Path.Combine(Folder,"platform-files.json")));
        foreach(var entry in map) {
            if (entry.Key=="apps/via-release.apk") continue; // obtained from publisher on first use
            string path=Path.Combine(Folder,"platform",entry.Key.Replace('/',Path.DirectorySeparatorChar));
            using(var f=File.OpenRead(path))
            using(var sha=SHA256.Create()) {
                if (BitConverter.ToString(sha.ComputeHash(f)).Replace("-","").ToLowerInvariant()!=entry.Value)
                    throw new IOException("文件校验失败："+entry.Key);
            }
        }
        Console.WriteLine("安装包自检通过。");
    }

    static long FreeBytes() {
        using(var root=Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Lxss")) {
            if (root!=null) foreach(var name in root.GetSubKeyNames()) {
                using(var d=root.OpenSubKey(name)) {
                    if (d==null || !String.Equals(d.GetValue("DistributionName") as string,Distro,StringComparison.OrdinalIgnoreCase)) continue;
                    var path=Environment.ExpandEnvironmentVariables((string)d.GetValue("BasePath"));
                    if (path.StartsWith(@"\\?\")) path=path.Substring(4);
                    return new DriveInfo(Path.GetPathRoot(path)).AvailableFreeSpace;
                }
            }
        }
        throw new IOException("无法确认 Ubuntu-24.04 所在 Windows 磁盘的可用空间。");
    }

    static void Dependencies() {
        Console.WriteLine("Windows 11 x64 首次准备（管理员终端）：\nwsl --install -d Ubuntu-24.04\nwsl --update\nwinget install --interactive --exact --id dorssel.usbipd-win\nwinget install --exact --id Google.PlatformTools\n按 Windows 提示重启，并打开 Ubuntu-24.04 完成账户设置。");
    }

    static void Wizard(string action) {
        string kernel=Run("wsl",true,false,"-d",Distro,"--exec","uname","-r").Trim();
        if (kernel.IndexOf("WSL2",StringComparison.OrdinalIgnoreCase)<0)
            throw new IOException("需要 WSL 2 模式的 Ubuntu-24.04。请运行 wsl -l -v 查看。");
        if (action!="download") ProgramPath("usbipd");
        Run("wsl",false,false,"-d",Distro,"--cd",Folder,"--exec","python3","-B","wizard.py","--windows-action",action);
    }

    static int Main(string[] args) {
        bool interactive=args.Length==0;
        try {
            if (!interactive) {
                if (args.Length!=1) throw new ArgumentException("参数无效");
                if (args[0]=="--connect-loader") Bridge();
                else if (args[0]=="--free-bytes") Console.WriteLine(FreeBytes());
                else if (args[0]=="--self-test") CheckBundle();
                else throw new ArgumentException("参数无效");
                return 0;
            }
            Console.WriteLine("Cherry Android · 有道词典笔 3 · Windows 测试版\n1  安装双系统\n2  下载上游文件\n3  从自己的备份恢复\n4  检查安装包\n5  首次准备 Windows 环境\n回车退出");
            string choice=Console.ReadLine();
            if (choice=="1") Wizard("install");
            else if (choice=="2") Wizard("download");
            else if (choice=="3") Wizard("restore");
            else if (choice=="4") CheckBundle();
            else if (choice=="5") Dependencies();
            else return 0;
            return 0;
        } catch(Exception e) {
            Console.Error.WriteLine(e.Message);
            if(interactive) Dependencies();
            return 1;
        } finally {
            if(interactive) { Console.WriteLine("按回车关闭窗口。"); Console.ReadLine(); }
        }
    }
}
