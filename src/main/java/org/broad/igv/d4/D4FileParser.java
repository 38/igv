package org.broad.igv.d4;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

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
        this.end = end + 1;
    }

    public int getEnd() {
        return end - 1;
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
        StringBuilder sb = new StringBuilder();
        sb.append(start);
        sb.append("-");
        sb.append(end);
        sb.append(":");
        sb.append(value);
        return sb.toString();
    }
}

public class D4FileParser extends AbstractDataSource {

    private long d4_handle;

    private HashMap<String, String> chrom_map;

    public D4FileParser(String path, Genome genome) throws FileNotFoundException {
        super(genome);

        this.d4_handle = d4_open(path);
        this.chrom_map = new HashMap();
        String[] d4_chroms = d4_chrom_name(d4_handle);
        for(int i = 0; i < d4_chroms.length; i++) {
            String ui_name = genome==null?null:genome.getCanonicalChrName(d4_chroms[i]);
            ui_name = ui_name == null? d4_chroms[i] : ui_name;
            chrom_map.put(ui_name, d4_chroms[i]);
        }

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
        Iterator it = chrom_map.entrySet().iterator();
        double ret = 0;
        long size = 0;
        while(it.hasNext()) {
            Entry entry = (Entry)it.next();
            String name = (String)entry.getKey();
            List<LocusScore> stat = this.getPrecomputedSummaryScores(name, 0, 1000000, 0);
            for(Iterator vit = stat.iterator(); vit.hasNext();) {
                LocusScore score = (LocusScore) vit.next();
                if(Double.isNaN(score.getScore()))  continue;
                ret += score.getScore() * (score.getEnd() - score.getStart());
                size += score.getEnd() - score.getStart();
            }
        }

        return ret / (double) size * 1.5;
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
        String d4_chr = chrom_map.get(chr);
        if(d4_chr == null) d4_chr = chr;
        
        double nBins = 700.0;//Math.pow(2, zoom);
        if(nBins > endLocation - startLocation) {
            nBins = endLocation - startLocation;
        }
        long result = d4_run_stat(d4_handle, 0, d4_chr, startLocation, endLocation, (int)nBins);

        List<LocusScore> ret = new ArrayList<LocusScore>();
        int size = d4_stat_size(result);
        for(int i = 0 ; i < size; i ++) {
            int left = d4_stat_get_begin(result, i);
            int right = d4_stat_get_end(result, i);
            float value = 0;
             switch(this.getWindowFunction().getValue()) {
                case "Maximum":  
                    value = d4_stat_get_max(result, i);
                    break;
                case "Minimum":
                    value = d4_stat_get_min(result, i);
                    break;
                 default:
                    value = d4_stat_get_value(result, i);
             }

            LocusScore score = new SummizedValue(left, right, value);
        if(size > 1000 && this.getWindowFunction().getValue() == "Minimum") {
                System.out.println(score.getValueString(0.0, 0, this.getWindowFunction()));
            }
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

    private native static String[] d4_chrom_name(long handle);

    private native static long d4_run_stat(long handle, int opcode, String chr, int start, int end, int count);

    private native static int d4_stat_size(long handle);

    private native static float d4_stat_get_value(long handle, int offset);

    private native static int d4_stat_get_begin(long handle, int offset);

    private native static int d4_stat_get_end(long handle, int offset);

    private native static int d4_stat_get_max(long handle, int offset);

    private native static int d4_stat_get_min(long handle, int offset);

    private native static int d4_stat_free(long handle);


    static {
        System.loadLibrary("d4-igv");
    }
}