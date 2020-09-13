package org.pytorch.helloworld;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.qzs.android.fuzzybackgroundlibrary.Fuzzy_Background;

public class PortraitModel {
    public static Bitmap simple(Context context, Bitmap original, Bitmap mask){
        int original_width = original.getWidth();
        int original_height = original.getHeight();

        int mask_width = mask.getWidth();
        int mask_height = mask.getHeight();
        if (original_width != mask_width || original_height != mask_height){
            return null;
        }
        else{
            Long current = System.currentTimeMillis();
            Bitmap blurd_image = Fuzzy_Background.with(context).bitmap(original.copy(Bitmap.Config.ARGB_8888,true)).radius(20).scale(1).blur();
            System.out.print("$$$$$$$$$$$$$");
            System.out.println(System.currentTimeMillis() - current);
            Bitmap result = Bitmap.createBitmap(original_width, original_height, Bitmap.Config.ARGB_8888);
            for (int i=0; i< mask_width; i++){
                for (int j=0; j<mask_height; j++){
                    //System.out.println(mask.getPixel(i, j));
                    if (mask.getPixel(i, j) == Color.WHITE){
                        result.setPixel(i, j, blurd_image.getPixel(i, j));
                    }else {
                        result.setPixel(i, j, original.getPixel(i, j));
                    }
                }
            }
            return result;
        }
    }

    public static Bitmap proprocess(Context context, Bitmap original, Bitmap mask){
        int original_width = original.getWidth();
        int original_height = original.getHeight();

        int mask_width = mask.getWidth();
        int mask_height = mask.getHeight();

        if (original_width != mask_width || original_height != mask_height){
            return null;
        }else {
            Bitmap blurd_image_1 = Fuzzy_Background.with(context).bitmap(original.copy(Bitmap.Config.ARGB_8888,true)).radius(7).scale(1).blur();
            Bitmap blurd_image_2 = Fuzzy_Background.with(context).bitmap(original.copy(Bitmap.Config.ARGB_8888,true)).radius(10).scale(1).blur();
            Bitmap blurd_image_3 = Fuzzy_Background.with(context).bitmap(original.copy(Bitmap.Config.ARGB_8888,true)).radius(13).scale(1).blur();
            Bitmap blurd_image_4 = Fuzzy_Background.with(context).bitmap(original.copy(Bitmap.Config.ARGB_8888,true)).radius(20).scale(1).blur();
            Bitmap result = Bitmap.createBitmap(mask_width, mask_height, Bitmap.Config.ARGB_8888);
            for (int i=0; i< mask_width; i++){
                for (int j=0; j<mask_height; j++){
                    //System.out.println(mask.getPixel(i, j));
                    int color = mask.getPixel(i, j);
                    switch (color){
                        case Color.BLACK: result.setPixel(i, j, original.getPixel(i, j));
                            break;
                        case Color.BLUE: result.setPixel(i, j, blurd_image_1.getPixel(i, j));
                            break;
                        case Color.GREEN: result.setPixel(i, j, blurd_image_2.getPixel(i, j));
                            break;
                        case Color.GRAY: result.setPixel(i, j, blurd_image_3.getPixel(i, j));
                            break;
                        default: result.setPixel(i, j, blurd_image_4.getPixel(i, j));
                    }
                }
            }
            return result;
        }

    }

}
