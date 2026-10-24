import android.graphics.Bitmap;
import android.os.Parcel;
public class T {
    public static void main(String[] a) {
        int[][] sz = {{32,32},{128,128},{256,256},{480,270},{640,360},{1280,720}};
        for (int[] s : sz) {
            try {
                Bitmap b = Bitmap.createBitmap(s[0], s[1], Bitmap.Config.ARGB_8888);
                Parcel p = Parcel.obtain();
                b.writeToParcel(p, 0);
                System.out.println(s[0] + "x" + s[1] + " OK dataSize=" + p.dataSize());
            } catch (Throwable t) { System.out.println(s[0] + "x" + s[1] + " FAIL " + t); }
        }
    }
}
