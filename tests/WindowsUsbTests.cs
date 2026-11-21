using System;
using System.IO;
internal static class WindowsUsbTests {
    static void Assert(bool ok) { if (!ok) throw new Exception("Windows USB test failed"); }
    static void Reject(Action op) { try { op(); } catch(IOException) { return; } throw new Exception("Expected rejection"); }
    public static void Main() {
        string row="{\"InstanceId\":\"USB\\\\VID_2207&PID_330D\\\\ABC\",\"BusId\":\"1-3\",\"PersistedGuid\":null,\"ClientIPAddress\":null}";
        Func<string,string> inventory=rows=>"{\"Devices\":["+rows+"]}";
        Assert(CherryWindows.Loader(inventory(row)).BusId=="1-3");
        Assert(CherryWindows.Loader(inventory(row.Replace("330D","0001")))==null);
        Assert(CherryWindows.Loader(inventory(row.Replace("\"1-3\"","null")))==null);
        Reject(()=>CherryWindows.Loader(inventory(row+","+row)));
        Reject(()=>CherryWindows.Loader(inventory(row.Replace("1-3","not-a-bus"))));
        string adb="List of devices attached\r\n0123456789ABCDEF\tdevice\r\n";
        Assert(CherryWindows.AdbSerial(adb)=="0123456789ABCDEF");
        Reject(()=>CherryWindows.AdbSerial(adb.Replace("\tdevice","\tunauthorized")));
        Reject(()=>CherryWindows.AdbSerial(adb+adb));
        Assert(CherryWindows.Quote("a b")=="\"a b\"");
        Assert(CherryWindows.Quote("C:\\")=="\"C:\\\\\"");
        Console.WriteLine("Windows USB enumeration, authorization state and argument tests passed");
    }
}
