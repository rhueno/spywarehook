package noface.browsers.win;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface K32File extends StdCallLibrary {

    K32File INSTANCE = Native.load("kernel32", K32File.class, W32APIOptions.UNICODE_OPTIONS);

    int GetFileSize(WinNT.HANDLE hFile, com.sun.jna.ptr.IntByReference lpFileSizeHigh);
    int SetFilePointer(WinNT.HANDLE hFile, int lDistanceToMove, com.sun.jna.ptr.IntByReference lpDistanceToMoveHigh, int dwMoveMethod);
    int GetFileType(WinNT.HANDLE hFile);
}
