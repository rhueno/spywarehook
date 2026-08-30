package noface.browsers.win;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class WinCrypt {

    public static class DATA_BLOB extends Structure {
        public int cbData;
        public Pointer pbData;

        public DATA_BLOB() {}

        public DATA_BLOB(byte[] data) {
            cbData = data.length;
            pbData = new Memory(data.length);
            pbData.write(0, data, 0, data.length);
        }

        public byte[] getData() {
            if (pbData == null || cbData <= 0) return new byte[0];
            return pbData.getByteArray(0, cbData);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cbData", "pbData");
        }
    }

    private WinCrypt() {}
}
