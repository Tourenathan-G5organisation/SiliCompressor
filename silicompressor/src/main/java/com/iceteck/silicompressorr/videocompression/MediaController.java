package com.iceteck.silicompressorr.videocompression;

/**
 * @Author By Jorge E. Hernandez (@lalongooo) 2015
 * @Co-Author Akah Larry (@larrytech7) 2017
 */

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressLint("NewApi")
public class MediaController {

    public static File cachedFile;
    public String path;

    public final static String MIME_TYPE = "video/avc";
    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;
    private final static int PROCESSOR_TYPE_INTEL = 2;
    private final static int PROCESSOR_TYPE_MTK = 3;
    private final static int PROCESSOR_TYPE_SEC = 4;
    private final static int PROCESSOR_TYPE_TI = 5;
    private static volatile MediaController Instance = null;
    private boolean videoConvertFirstWrite = true;

    //Default values
    private final static int DEFAULT_VIDEO_WIDTH = 640;
    private final static int DEFAULT_VIDEO_HEIGHT = 360;
    private final static int DEFAULT_VIDEO_BITRATE = 450000;

    public static MediaController getInstance() {
        MediaController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MediaController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MediaController();
                }
            }
        }
        return localInstance;
    }

    @SuppressLint("NewApi")
    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int lastColorFormat = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat;
                if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                    return colorFormat;
                }
            }
        }
        return lastColorFormat;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    public native static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap);

    private void didWriteData(final boolean last, final boolean error) {
        final boolean firstWrite = videoConvertFirstWrite;
        if (firstWrite) {
            videoConvertFirstWrite = false;
        }
    }

    public static class VideoConvertRunnable implements Runnable {

        private String videoPath;
        private File destDirectory;

        private VideoConvertRunnable(String videoPath, File dest) {
            this.videoPath = videoPath;
            this.destDirectory = dest;
        }

        public static void runConversion(final String videoPath, final File dest) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        VideoConvertRunnable wrapper = new VideoConvertRunnable(videoPath, dest);
                        Thread th = new Thread(wrapper, "VideoConvertRunnable");
                        th.start();
                        th.join();
                    } catch (Exception e) {
                        Log.e("tmessages", e.getMessage());
                    }
                }
            }).start();
        }

        @Override
        public void run() {
            MediaController.getInstance().convertVideo(videoPath, destDirectory);
        }
    }

    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo lastCodecInfo = null;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    lastCodecInfo = codecInfo;
                    if (!lastCodecInfo.getName().equals("OMX.SEC.avc.enc")) {
                        return lastCodecInfo;
                    } else if (lastCodecInfo.getName().equals("OMX.SEC.AVC.Encoder")) {
                        return lastCodecInfo;
                    }
                }
            }
        }
        return lastCodecInfo;
    }

    /**
     * Background conversion for queueing tasks
     * @param path source file to compress
     * @param dest destination directory to put result
     */

public void scheduleVideoConvert(String path, File dest) {
        startVideoConvertFromQueue(path, dest);
    }

    private void startVideoConvertFromQueue(String path, File dest) {
        VideoConvertRunnable.runConversion(path, dest);
    }

    @TargetApi(16)
    private long readAndWriteTrack(MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
        int trackIndex = selectTrack(extractor, isAudio);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            int muxerTrackIndex = mediaMuxer.addTrack(trackFormat, isAudio);
            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;

            while (!inputDone) {

                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);

                    if (info.size < 0) {
                        info.size = 0;
                        eof = true;
                    } else {
                        info.presentationTimeUs = extractor.getSampleTime();
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            if (mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, isAudio)) {
                                // didWriteData(messageObject, file, false, false);
                            }
                            extractor.advance();
                        } else {
                            eof = true;
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }

    @TargetApi(16)
    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    /**
     * Perform the actual video compression. Processes the frames and does the magic
     * Width, height and bitrate are now default
     * @param sourcePath the source uri for the file as per
     * @param destDir the destination directory where compressed video is eventually saved
     * @return
     */
    public boolean  convertVideo(final String sourcePath, File destDir)
    {
        return convertVideo( sourcePath, destDir, 0, 0, 0 );
    }

    /**
     * Perform the actual video compression. Processes the frames and does the magic
     * @param sourcePath the source uri for the file as per
     * @param destDir the destination directory where compressed video is eventually saved
     * @param outWidth the target width of the converted video, 0 is default
     * @param outHeight the target height of the converted video, 0 is default
     * @param outBitrate the target bitrate of the converted video, 0 is default
     * @return
     */
    @TargetApi(16)
    public boolean  convertVideo(final String sourcePath, File destDir, int outWidth, int outHeight, int outBitrate) {
        this.path=sourcePath;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

        long startTime = -1;
        long endTime = -1;

        int resultWidth = outWidth > 0 ? outWidth : DEFAULT_VIDEO_WIDTH;
        int resultHeight = outHeight > 0 ? outHeight : DEFAULT_VIDEO_HEIGHT;

        int rotationValue = Integer.valueOf(rotation);
        int originalWidth = Integer.valueOf(width);
        int originalHeight = Integer.valueOf(height);

        int bitrate = outBitrate > 0 ? outBitrate : DEFAULT_VIDEO_BITRATE;
        int rotateRender = 0;

        File cacheFile = new File(destDir,
                "VIDEO_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4"
        );

        if (Build.VERSION.SDK_INT < 18 && resultHeight > resultWidth && resultWidth != originalWidth && resultHeight != originalHeight) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
            rotationValue = 90;
            rotateRender = 270;
        } else if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 270;
            } else if (rotationValue == 180) {
                rotateRender = 180;
                rotationValue = 0;
            } else if (rotationValue == 270) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 90;
            }
        }


        File inputFile = new File(path);
        if (!inputFile.canRead()) {
            didWriteData(true, true);
            return false;
        }

        videoConvertFirstWrite = true;
        boolean error = false;
        long videoStartTime = startTime;

        long time = System.currentTimeMillis();

        if (resultWidth != 0 && resultHeight != 0) {
            MP4Builder mediaMuxer = null;
            MediaExtractor extractor = null;

            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                Mp4Movie movie = new Mp4Movie();
                movie.setCacheFile(cacheFile);
                movie.setRotation(rotationValue);
                movie.setSize(resultWidth, resultHeight);
                mediaMuxer = new MP4Builder().createMovie(movie);
                extractor = new MediaExtractor();
                extractor.setDataSource(inputFile.toString());


                if (resultWidth != originalWidth || resultHeight != originalHeight) {
                    int videoIndex;
                    videoIndex = selectTrack(extractor, false);

                    if (videoIndex >= 0) {
                        MediaCodec decoder = null;
                        MediaCodec encoder = null;
                        InputSurface inputSurface = null;
                        OutputSurface outputSurface = null;

                        try {
                            long videoTime = -1;
                            boolean outputDone = false;
                            boolean inputDone = false;
                            boolean decoderDone = false;
                            int swapUV = 0;
                            int videoTrackIndex = -5;

                            int colorFormat;
                            int processorType = PROCESSOR_TYPE_OTHER;
                            String manufacturer = Build.MANUFACTURER.toLowerCase();
                            if (Build.VERSION.SDK_INT < 18) {
                                MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
                                colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
                                if (colorFormat == 0) {
                                    throw new RuntimeException("no supported color format");
                                }
                                String codecName = codecInfo.getName();
                                if (codecName.contains("OMX.qcom.")) {
                                    processorType = PROCESSOR_TYPE_QCOM;
                                    if (Build.VERSION.SDK_INT == 16) {
                                        if (manufacturer.equals("lge") || manufacturer.equals("nokia")) {
                                            swapUV = 1;
                                        }
                                    }
                                } else if (codecName.contains("OMX.Intel.")) {
                                    processorType = PROCESSOR_TYPE_INTEL;
                                } else if (codecName.equals("OMX.MTK.VIDEO.ENCODER.AVC")) {
                                    processorType = PROCESSOR_TYPE_MTK;
                                } else if (codecName.equals("OMX.SEC.AVC.Encoder")) {
                                    processorType = PROCESSOR_TYPE_SEC;
                                    swapUV = 1;
                                } else if (codecName.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                                    processorType = PROCESSOR_TYPE_TI;
                                }
                                Log.e("tmessages", "codec = " + codecInfo.getName() + " manufacturer = " + manufacturer + "device = " + Build.MODEL);
                            } else {
                                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                            }
                            Log.e("tmessages", "colorFormat = " + colorFormat);

                            int resultHeightAligned = resultHeight;
                            int padding = 0;
                            int bufferSize = resultWidth * resultHeight * 3 / 2;
                            if (processorType == PROCESSOR_TYPE_OTHER) {
                                if (resultHeight % 16 != 0) {
                                    resultHeightAligned += (16 - (resultHeight % 16));
                                    padding = resultWidth * (resultHeightAligned - resultHeight);
                                    bufferSize += padding * 5 / 4;
                                }
                            } else if (processorType == PROCESSOR_TYPE_QCOM) {
                                if (!manufacturer.toLowerCase().equals("lge")) {
                                    int uvoffset = (resultWidth * resultHeight + 2047) & ~2047;
                                    padding = uvoffset - (resultWidth * resultHeight);
                                    bufferSize += padding;
                                }
                            } else if (processorType == PROCESSOR_TYPE_TI) {
                                //resultHeightAligned = 368;
                                //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
                                //resultHeightAligned += (16 - (resultHeight % 16));
                                //padding = resultWidth * (resultHeightAligned - resultHeight);
                                //bufferSize += padding * 5 / 4;
                            } else if (processorType == PROCESSOR_TYPE_MTK) {
                                if (manufacturer.equals("baidu")) {
                                    resultHeightAligned += (16 - (resultHeight % 16));
                                    padding = resultWidth * (resultHeightAligned - resultHeight);
                                    bufferSize += padding * 5 / 4;
                                }
                            }

                            extractor.selectTrack(videoIndex);
                            if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }
                            MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);

                            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate != 0 ? bitrate : 921600);
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
                            if (Build.VERSION.SDK_INT < 18) {
                                outputFormat.setInteger("stride", resultWidth + 32);
                                outputFormat.setInteger("slice-height", resultHeight);
                            }

                            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                            if (Build.VERSION.SDK_INT >= 18) {
                                inputSurface = new InputSurface(encoder.createInputSurface());
                                inputSurface.makeCurrent();
                            }
                            encoder.start();

                            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
                            if (Build.VERSION.SDK_INT >= 18) {
                                outputSurface = new OutputSurface();
                            } else {
                                outputSurface = new OutputSurface(resultWidth, resultHeight, rotateRender);
                            }
                            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                            decoder.start();

                            final int TIMEOUT_USEC = 2500;
                            ByteBuffer[] decoderInputBuffers = null;
                            ByteBuffer[] encoderOutputBuffers = null;
                            ByteBuffer[] encoderInputBuffers = null;
                            if (Build.VERSION.SDK_INT < 21) {
                                decoderInputBuffers = decoder.getInputBuffers();
                                encoderOutputBuffers = encoder.getOutputBuffers();
                                if (Build.VERSION.SDK_INT < 18) {
                                    encoderInputBuffers = encoder.getInputBuffers();
                                }
                            }

                            while (!outputDone) {
                                if (!inputDone) {
                                    boolean eof = false;
                                    int index = extractor.getSampleTrackIndex();
                                    if (index == videoIndex) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            ByteBuffer inputBuf;
                                            if (Build.VERSION.SDK_INT < 21) {
                                                inputBuf = decoderInputBuffers[inputBufIndex];
                                            } else {
                                                inputBuf = decoder.getInputBuffer(inputBufIndex);
                                            }
                                            int chunkSize = extractor.readSampleData(inputBuf, 0);
                                            if (chunkSize < 0) {
                                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                inputDone = true;
                                            } else {
                                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                                extractor.advance();
                                            }
                                        }
                                    } else if (index == -1) {
                                        eof = true;
                                    }
                                    if (eof) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            inputDone = true;
                                        }
                                    }
                                }

                                boolean decoderOutputAvailable = !decoderDone;
                                boolean encoderOutputAvailable = true;
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        encoderOutputAvailable = false;
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encoderOutputBuffers = encoder.getOutputBuffers();
                                        }
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                        MediaFormat newFormat = encoder.getOutputFormat();
                                        if (videoTrackIndex == -5) {
                                            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                        }
                                    } else if (encoderStatus < 0) {
                                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                    } else {
                                        ByteBuffer encodedData;
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encodedData = encoderOutputBuffers[encoderStatus];
                                        } else {
                                            encodedData = encoder.getOutputBuffer(encoderStatus);
                                        }
                                        if (encodedData == null) {
                                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                        }
                                        if (info.size > 1) {
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, false)) {
                                                    didWriteData(false, false);
                                                }
                                            } else if (videoTrackIndex == -5) {
                                                byte[] csd = new byte[info.size];
                                                encodedData.limit(info.offset + info.size);
                                                encodedData.position(info.offset);
                                                encodedData.get(csd);
                                                ByteBuffer sps = null;
                                                ByteBuffer pps = null;
                                                for (int a = info.size - 1; a >= 0; a--) {
                                                    if (a > 3) {
                                                        if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                            sps = ByteBuffer.allocate(a - 3);
                                                            pps = ByteBuffer.allocate(info.size - (a - 3));
                                                            sps.put(csd, 0, a - 3).position(0);
                                                            pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                            break;
                                                        }
                                                    } else {
                                                        break;
                                                    }
                                                }

                                                MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                                if (sps != null && pps != null) {
                                                    newFormat.setByteBuffer("csd-0", sps);
                                                    newFormat.setByteBuffer("csd-1", pps);
                                                }
                                                videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                            }
                                        }
                                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                        encoder.releaseOutputBuffer(encoderStatus, false);
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        continue;
                                    }

                                    if (!decoderDone) {
                                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            decoderOutputAvailable = false;
                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            MediaFormat newFormat = decoder.getOutputFormat();
                                            Log.e("tmessages", "newFormat = " + newFormat);
                                        } else if (decoderStatus < 0) {
                                            throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                        } else {
                                            boolean doRender;
                                            if (Build.VERSION.SDK_INT >= 18) {
                                                doRender = info.size != 0;
                                            } else {
                                                doRender = info.size != 0 || info.presentationTimeUs != 0;
                                            }
                                            if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                                inputDone = true;
                                                decoderDone = true;
                                                doRender = false;
                                                info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                            }
                                            if (startTime > 0 && videoTime == -1) {
                                                if (info.presentationTimeUs < startTime) {
                                                    doRender = false;
                                                    Log.e("tmessages", "drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                                } else {
                                                    videoTime = info.presentationTimeUs;
                                                }
                                            }
                                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                                            if (doRender) {
                                                boolean errorWait = false;
                                                try {
                                                    outputSurface.awaitNewImage();
                                                } catch (Exception e) {
                                                    errorWait = true;
                                                    Log.e("tmessages", e.getMessage());
                                                }
                                                if (!errorWait) {
                                                    if (Build.VERSION.SDK_INT >= 18) {
                                                        outputSurface.drawImage(false);
                                                        inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                                        inputSurface.swapBuffers();
                                                    } else {
                                                        int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                        if (inputBufIndex >= 0) {
                                                            outputSurface.drawImage(true);
                                                            ByteBuffer rgbBuf = outputSurface.getFrame();
                                                            ByteBuffer yuvBuf = encoderInputBuffers[inputBufIndex];
                                                            yuvBuf.clear();
                                                            convertVideoFrame(rgbBuf, yuvBuf, colorFormat, resultWidth, resultHeight, padding, swapUV);
                                                            encoder.queueInputBuffer(inputBufIndex, 0, bufferSize, info.presentationTimeUs, 0);
                                                        } else {
                                                            Log.e("tmessages", "input buffer not available");
                                                        }
                                                    }
                                                }
                                            }
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                decoderOutputAvailable = false;
                                                Log.e("tmessages", "decoder stream end");
                                                if (Build.VERSION.SDK_INT >= 18) {
                                                    encoder.signalEndOfInputStream();
                                                } else {
                                                    int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                    if (inputBufIndex >= 0) {
                                                        encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (videoTime != -1) {
                                videoStartTime = videoTime;
                            }
                        } catch (Exception e) {
                            Log.e("tmessages", e.getMessage());
                            error = true;
                        }

                        extractor.unselectTrack(videoIndex);

                        if (outputSurface != null) {
                            outputSurface.release();
                        }
                        if (inputSurface != null) {
                            inputSurface.release();
                        }
                        if (decoder != null) {
                            decoder.stop();
                            decoder.release();
                        }
                        if (encoder != null) {
                            encoder.stop();
                            encoder.release();
                        }
                    }
                } else {
                    long videoTime = readAndWriteTrack(extractor, mediaMuxer, info, startTime, endTime, cacheFile, false);
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
                if (!error) {
                    readAndWriteTrack(extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true);
                }
            } catch (Exception e) {
                error = true;
                Log.e("tmessages", e.getMessage());
            } finally {
                if (extractor != null) {
                    extractor.release();
                }
                if (mediaMuxer != null) {
                    try {
                        mediaMuxer.finishMovie(false);
                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? "An error occurred at the finishMovie" : e.getMessage();
                        Log.e("tmessages", msg);
                    }
                }
                Log.e("tmessages", "time = " + (System.currentTimeMillis() - time));
            }
        } else {
            didWriteData(true, true);
            return false;
        }
        didWriteData(true, error);

        cachedFile=cacheFile;

       /* File fdelete = inputFile;
        if (fdelete.exists()) {
            if (fdelete.delete()) {
               Log.e("file Deleted :" ,inputFile.getPath());
            } else {
                Log.e("file not Deleted :" , inputFile.getPath());
            }
        }*/

        //inputFile.delete();
        Log.e("ViratPath",path+"");
        Log.e("ViratPath",cacheFile.getPath()+"");
        Log.e("ViratPath",inputFile.getPath()+"");


       /* Log.e("ViratPath",path+"");
        File replacedFile = new File(path);

        FileOutputStream fos = null;
        InputStream inputStream = null;
        try {
            fos = new FileOutputStream(replacedFile);
             inputStream = new FileInputStream(cacheFile);
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            inputStream.close();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
*/

    //    cacheFile.delete();

       /* try {
           // copyFile(cacheFile,inputFile);
            //inputFile.delete();
            FileUtils.copyFile(cacheFile,inputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
         // cacheFile.delete();
       // inputFile.delete();
        return true;
    }

    public static void copyFile(File src, File dst) throws IOException
    {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try
        {
            inChannel.transferTo(1, inChannel.size(), outChannel);
        }
        finally
        {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }
}