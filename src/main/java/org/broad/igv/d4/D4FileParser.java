package org.broad.igv.d4;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.broad.igv.data.AbstractDataSource;
import org.broad.igv.data.DataTile;
import org.broad.igv.feature.LocusScore;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.TrackType;
import org.broad.igv.track.WindowFunction;

class SummizedValue implements LocusScore {

    int start, end;
    float value;

    SummizedValue(int left, int right, float value) {
        start = left;
        end = right;
        this.value = value;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public int getEnd() {
        return end;
    }
    
    public int getStart() {
        return start;
    }

    public String getContig() {
        return null;
    }

    public float getScore() {
        return value;
    }

    public String getValueString(double position, int mouseX, WindowFunction windowFunction) {
        return "Test";
    }
}

public class D4FileParser extends AbstractDataSource {

    private long d4_handle;

    public D4FileParser(String path, Genome genome) throws FileNotFoundException {
        super(genome);

        this.d4_handle = d4_open(path);

        if (this.d4_handle == 0) {
            throw new FileNotFoundException();
        }
    }

    public void finalize() {
        if (this.d4_handle != 0) {
            d4_close(d4_handle);
        }
    }

    @Override
    public double getDataMax() {
        return 60;
    }

    @Override
    public double getDataMin() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public TrackType getTrackType() {
        return TrackType.COVERAGE;
    }

    @Override
    protected DataTile getRawData(String chr, int startLocation, int endLocation) {
        return null;
    }

    @Override
    protected List<LocusScore> getPrecomputedSummaryScores(String chr, int startLocation, int endLocation, int zoom) {

        if(chr == "All" || startLocation < 0 || endLocation < 0) {
            return new ArrayList();
        }
        System.out.println("DownSampled:" + chr + " " + Integer.toString(startLocation) + "-" + Integer.toString(endLocation));
        double nBins = 1024.0;//Math.pow(2, zoom);
        if(nBins > endLocation - startLocation) {
            nBins = endLocation - startLocation;
        }
        long result = d4_run_stat(d4_handle, 0, chr.substring(3), startLocation, endLocation, (int)nBins);


        List<LocusScore> ret = new ArrayList<LocusScore>();
        int size = d4_stat_size(result);
        for(int i = 0 ; i < size; i ++) {
            int left = d4_stat_get_begin(result, i);
            int right = d4_stat_get_end(result, i);
            float value = d4_stat_get_value(result, i);
            LocusScore score = new SummizedValue(left, right, value);
            ret.add(score);
        }

        d4_stat_free(result);

        return ret;
    }

    @Override
    public int getLongestFeature(String chr) {
        return 1;
    }
    
    private native static long d4_open(String path);

    private native static void d4_close(long handle);

    private native static long d4_run_stat(long handle, int opcode, String chr, int start, int end, int count);

    private native static int d4_stat_size(long handle);

    private native static float d4_stat_get_value(long handle, int offset);

    private native static int d4_stat_get_begin(long handle, int offset);

    private native static int d4_stat_get_end(long handle, int offset);

    private native static int d4_stat_free(long handle);


    static {
        System.out.println("Start loading D4");
        System.loadLibrary("d4-igv");
        System.out.println("D4 load completed");
    }
}