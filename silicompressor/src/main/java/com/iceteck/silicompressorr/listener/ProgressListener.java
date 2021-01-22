package com.iceteck.silicompressorr.listener;

/**
 * Created by tapos-datta on 1/22/21.
 */
public interface ProgressListener {

    void onProgress(float progress);

    void onComplete();
}
