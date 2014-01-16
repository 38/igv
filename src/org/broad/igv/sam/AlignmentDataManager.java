/*
 * Copyright (c) 2007-2014 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */
package org.broad.igv.sam;

import com.google.common.eventbus.EventBus;
import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.PreferenceManager;
import org.broad.igv.feature.Range;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.sam.AlignmentTrack.SortOption;
import org.broad.igv.sam.reader.AlignmentReaderFactory;
import org.broad.igv.track.RenderContext;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.event.DataLoadedEvent;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.ProgressMonitor;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.NamedRunnable;
import org.broad.igv.util.ResourceLocator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.*;

public class AlignmentDataManager implements IAlignmentDataManager {

    private static Logger log = Logger.getLogger(AlignmentDataManager.class);

    /**
     * Map of reference frame name -> alignment interval
     */
    //private Map<String, AlignmentInterval> loadedIntervalMap = new HashMap<String, AlignmentInterval>();
    private PositionMap<AlignmentInterval> loadedIntervalCache = new PositionMap<AlignmentInterval>();
    private PositionMap<PackedAlignments> packedAlignmentsCache = new PositionMap<PackedAlignments>();

    private HashMap<String, String> chrMappings = new HashMap();
    private volatile boolean isLoading = false;
    private AlignmentTileLoader reader;
    private CoverageTrack coverageTrack;

    private static final int MAX_ROWS = 1000000;
    private Map<String, PEStats> peStats;

    private AlignmentTrack.ExperimentType experimentType;

    private SpliceJunctionHelper.LoadOptions loadOptions;

    private Object loadLock = new Object();

    /**
     * This {@code EventBus} is typically used to notify listeners when new data
     * is loaded
     */
    private EventBus eventBus = new EventBus();

    private ResourceLocator locator;

    public AlignmentDataManager(ResourceLocator locator, Genome genome) throws IOException {
        this.locator = locator;
        reader = new AlignmentTileLoader(AlignmentReaderFactory.getReader(locator));
        peStats = new HashMap();
        initLoadOptions();
        initChrMap(genome);
    }

    void initLoadOptions() {
        this.loadOptions = new SpliceJunctionHelper.LoadOptions();
    }

    /**
     * Create an alias -> chromosome lookup map.  Enable loading BAM files that use alternative names for chromosomes,
     * provided the alias has been defined  (e.g. 1 -> chr1,  etc).
     */
    private void initChrMap(Genome genome) {
        if (genome != null) {
            List<String> seqNames = reader.getSequenceNames();
            if (seqNames != null) {
                for (String chr : seqNames) {
                    String alias = genome.getChromosomeAlias(chr);
                    chrMappings.put(alias, chr);
                }
            }
        }
    }

    public void setExperimentType(AlignmentTrack.ExperimentType experimentType) {
        this.experimentType = experimentType;
    }

    public AlignmentTrack.ExperimentType getExperimentType() {
        return experimentType;
    }

    public AlignmentTileLoader getReader() {
        return reader;
    }

    public ResourceLocator getLocator() {
        return locator;
    }

    public Map<String, PEStats> getPEStats() {
        return peStats;
    }

    public boolean isPairedEnd() {
        return reader.isPairedEnd();
    }

    public boolean hasIndex() {
        return reader.hasIndex();
    }

    public void setCoverageTrack(CoverageTrack coverageTrack) {
        this.coverageTrack = coverageTrack;
    }

    public CoverageTrack getCoverageTrack() {
        return coverageTrack;
    }

    /**
     * The set of sequences found in the file.
     * May be null
     *
     * @return
     */
    public List<String> getSequenceNames() {
        return reader.getSequenceNames();
    }


    public boolean isIonTorrent() {
        Set<String> platforms = reader.getPlatforms();
        if (platforms != null) {
            return platforms.contains("IONTORRENT");
        }
        return false;
    }

    public Collection<AlignmentInterval> getLoadedIntervals() {
        return this.loadedIntervalCache.values();
    }

    public AlignmentInterval getLoadedInterval(Range range) {
        return loadedIntervalCache.get(range);
    }

    /**
     * Sort rows group by group
     *
     * @param option
     * @param location
     */
    public boolean sortRows(SortOption option, ReferenceFrame frame, double location, String tag) {
        PackedAlignments packedAlignments = packedAlignmentsCache.get(frame.getCurrentRange());
        AlignmentInterval interval = loadedIntervalCache.get(frame.getCurrentRange());
        if (packedAlignments == null || interval == null) {
            return false;
        }

        for (List<Row> alignmentRows : packedAlignments.values()) {
            for (Row row : alignmentRows) {
                row.updateScore(option, location, interval, tag);
            }
            Collections.sort(alignmentRows);
        }
        return true;
    }

    public void setViewAsPairs(boolean option, AlignmentTrack.RenderOptions renderOptions) {
        if (option == renderOptions.isViewPairs()) {
            return;
        }
        renderOptions.setViewPairs(option);

        repackAlignments(FrameManager.getFrames(), renderOptions);
    }

//    private void repackAlignments(ReferenceFrame frame, boolean currentPairState, AlignmentTrack.RenderOptions renderOptions) {
//
//        if (currentPairState) {
//            PackedAlignments packedAlignments = packedAlignmentsCache.get(frame.getName());
//            if (packedAlignments == null) {
//                return;
//            }
//
//            List<Alignment> alignments = new ArrayList<Alignment>(Math.min(50000, packedAlignments.size() * 10000));
//            int intervalEnd = -1;
//            for (List<Row> alignmentRows : packedAlignments.values()) {
//                for (Row row : alignmentRows) {
//                    for (Alignment al : row.alignments) {
//                        intervalEnd = Math.max(intervalEnd, al.getEnd());
//                        if (al instanceof PairedAlignment) {
//                            PairedAlignment pair = (PairedAlignment) al;
//                            alignments.add(pair.firstAlignment);
//                            if (pair.secondAlignment != null) {
//                                alignments.add(pair.secondAlignment);
//                            }
//                        } else {
//                            alignments.add(al);
//                        }
//                    }
//                }
//            }
//
//
//            // ArrayHeapObjectSorter sorts in place (no additional memory required).
//            ArrayHeapObjectSorter<Alignment> heapSorter = new ArrayHeapObjectSorter();
//            heapSorter.sort(alignments, new Comparator<Alignment>() {
//                public int compare(Alignment alignment, Alignment alignment1) {
//                    return alignment.getStart() - alignment1.getStart();
//                }
//            });
//
//            AlignmentPacker packer = new AlignmentPacker();
//            AlignmentInterval oldInterval = packedAlignments
//            PackedAlignments tmp = packer.packAlignments(
//                    a,
//                    renderOptions);
//
//            packedAlignmentsCache.put(frame.getName(), tmp);
//
//        } else {
//            repackAlignments(frame, renderOptions);
//        }
//    }

    /**
     * Repack alignments across all frames
     * @see #repackAlignments(java.util.List, org.broad.igv.sam.AlignmentTrack.RenderOptions)
     * @param renderOptions
     */
    private boolean repackAlignmentsAllFrames(AlignmentTrack.RenderOptions renderOptions){
        return repackAlignments(FrameManager.getFrames(), renderOptions);
    }

    /**
     * Repack currently loaded alignments across provided frames
     * All relevant intervals must be loaded
     *
     * @param frameList
     * @param renderOptions
     * @return Whether repacking was performed
     * @see AlignmentPacker#packAlignments(List, org.broad.igv.sam.AlignmentTrack.RenderOptions)
     */
    public boolean repackAlignments(List<ReferenceFrame> frameList, AlignmentTrack.RenderOptions renderOptions) {

        List<AlignmentInterval> intervalList = new ArrayList<AlignmentInterval>(frameList.size());
        for(ReferenceFrame frame: frameList){
            AlignmentInterval loadedInterval = loadedIntervalCache.get(frame.getCurrentRange());

            if (loadedInterval == null) {
                return false;
            }
            intervalList.add(loadedInterval);
        }

        final AlignmentPacker alignmentPacker = new AlignmentPacker();
        PackedAlignments packedAlignments = alignmentPacker.packAlignments(intervalList, renderOptions);

        for(ReferenceFrame frame: frameList) this.packedAlignmentsCache.put(frame.getCurrentRange(), packedAlignments);
        return true;
    }


    public void load(RenderContext context,
                     AlignmentTrack.RenderOptions renderOptions,
                     boolean expandEnds) {

        synchronized (loadLock) {
            final String chr = context.getChr();
            final int start = (int) context.getOrigin();
            final int end = (int) context.getEndLocation();
            AlignmentInterval loadedInterval = loadedIntervalCache.get(context.getReferenceFrame().getCurrentRange());

            int adjustedStart = start;
            int adjustedEnd = end;
            int windowSize = PreferenceManager.getInstance().getAsInt(PreferenceManager.SAM_MAX_VISIBLE_RANGE) * 1000;
            int center = (end + start) / 2;
            int expand = Math.max(end - start, windowSize / 2);

            if (loadedInterval != null) {
                // First see if we have a loaded interval that fully contain the requested interval.
                // If so, we don't need to load it
                if (loadedInterval.contains(chr, start, end)) {
                    return;
                }
            }

            if (expandEnds) {
                adjustedStart = Math.max(0, Math.min(start, center - expand));
                adjustedEnd = Math.max(end, center + expand);
            }
            loadAlignments(chr, adjustedStart, adjustedEnd, renderOptions, context);
        }

    }

    public synchronized PackedAlignments getGroups(RenderContext context, AlignmentTrack.RenderOptions renderOptions) {
        load(context, renderOptions, false);
        Range range = context.getReferenceFrame().getCurrentRange();
        if(!packedAlignmentsCache.contains(range)){
            repackAlignmentsAllFrames(renderOptions);
        }
        return packedAlignmentsCache.get(context.getReferenceFrame().getCurrentRange());
    }

    public void clear() {
        // reader.clearCache();
        loadedIntervalCache.clear();
        packedAlignmentsCache.clear();
    }

    public synchronized void loadAlignments(final String chr, final int start, final int end,
                                            final AlignmentTrack.RenderOptions renderOptions,
                                            final RenderContext context) {

        if (isLoading || chr.equals(Globals.CHR_ALL)) {
            return;
        }

        isLoading = true;

        NamedRunnable runnable = new NamedRunnable() {

            public String getName() {
                return "loadAlignments";
            }

            public void run() {

                log.debug("Loading alignments: " + chr + ":" + start + "-" + end + " for " + AlignmentDataManager.this);

                AlignmentInterval loadedInterval = loadInterval(chr, start, end, renderOptions);
                loadedIntervalCache.put(loadedInterval.getRange(), loadedInterval);

                repackAlignments(Arrays.asList(context.getReferenceFrame()), renderOptions);
                getEventBus().post(new DataLoadedEvent(context));

                isLoading = false;
            }
        };
        LongRunningTask.submit(runnable);
    }

    AlignmentInterval loadInterval(String chr, int start, int end, AlignmentTrack.RenderOptions renderOptions) {

        String sequence = chrMappings.containsKey(chr) ? chrMappings.get(chr) : chr;

        DownsampleOptions downsampleOptions = new DownsampleOptions();

        final AlignmentTrack.BisulfiteContext bisulfiteContext =
                renderOptions != null ? renderOptions.bisulfiteContext : null;

        ProgressMonitor monitor = null;
        //Show cancel button
        if (IGV.hasInstance() && !Globals.isBatch() && !Globals.isHeadless()) {
            ActionListener cancelListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AlignmentTileLoader.cancelReaders();
                }
            };
            IGV.getInstance().getContentPane().getStatusBar().activateCancelButton(cancelListener);
        }

        SpliceJunctionHelper spliceJunctionHelper = new SpliceJunctionHelper(this.loadOptions);
        AlignmentTileLoader.AlignmentTile t = reader.loadTile(sequence, start, end, spliceJunctionHelper,
                downsampleOptions, peStats, bisulfiteContext, monitor);

        List<Alignment> alignments = t.getAlignments();
        List<DownsampledInterval> downsampledIntervals = t.getDownsampledIntervals();
        return new AlignmentInterval(chr, start, end, alignments, t.getCounts(), spliceJunctionHelper, downsampledIntervals, renderOptions);
    }

    /**
     * Find the first loaded interval for the specified chromosome and genomic {@code positon},
     * return the grouped alignments
     *
     * @param position
     * @param referenceFrame
     * @return alignmentRows, grouped and ordered by key
     */
    public PackedAlignments getGroupedAlignmentsContaining(double position, ReferenceFrame referenceFrame) {
        String chr = referenceFrame.getChrName();
        int start = (int) position;
        int end = start + 1;

        PackedAlignments packedAlignments = packedAlignmentsCache.get(referenceFrame.getCurrentRange());
        if (packedAlignments != null && packedAlignments.contains(chr, start, end)) {
            return packedAlignments;
        }
        return null;
    }

    public int getNLevels() {
        int nLevels = 0;
        for (PackedAlignments packedAlignments : packedAlignmentsCache.values()) {
            int intervalNLevels = packedAlignments.getNLevels();
            nLevels = Math.max(nLevels, intervalNLevels);
        }
        return nLevels;
    }

    /**
     * Get the maximum group count among all the loaded intervals.  Normally there is one interval, but there
     * can be multiple if viewing split screen.
     */
    public int getMaxGroupCount() {
        int groupCount = 0;
        for (PackedAlignments packedAlignments : packedAlignmentsCache.values()) {
            groupCount = Math.max(groupCount, packedAlignments.size());
        }

        return groupCount;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ex) {
                log.error("Error closing AlignmentQueryReader. ", ex);
            }
        }

    }

    public void updatePEStats(AlignmentTrack.RenderOptions renderOptions) {
        if (this.peStats != null) {
            for (PEStats stats : peStats.values()) {
                stats.compute(renderOptions.getMinInsertSizePercentile(), renderOptions.getMaxInsertSizePercentile());
            }
        }
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public SpliceJunctionHelper.LoadOptions getSpliceJunctionLoadOptions() {
        return loadOptions;
    }

    public void setMinJunctionCoverage(int minJunctionCoverage) {
        this.loadOptions = new SpliceJunctionHelper.LoadOptions(minJunctionCoverage, this.loadOptions.minReadFlankingWidth);
        for (AlignmentInterval interval : getLoadedIntervals()) {
            interval.getSpliceJunctionHelper().setLoadOptions(this.loadOptions);
        }
    }

    PositionMap getCache() {
        return this.loadedIntervalCache;
    }

    public static class DownsampleOptions {
        private boolean downsample;
        private int sampleWindowSize;
        private int maxReadCount;

        public DownsampleOptions() {
            PreferenceManager prefs = PreferenceManager.getInstance();
            init(prefs.getAsBoolean(PreferenceManager.SAM_DOWNSAMPLE_READS),
                    prefs.getAsInt(PreferenceManager.SAM_SAMPLING_WINDOW),
                    prefs.getAsInt(PreferenceManager.SAM_SAMPLING_COUNT));
        }

        DownsampleOptions(boolean downsample, int sampleWindowSize, int maxReadCount) {
            init(downsample, sampleWindowSize, maxReadCount);
        }

        private void init(boolean downsample, int sampleWindowSize, int maxReadCount) {
            this.downsample = downsample;
            this.sampleWindowSize = sampleWindowSize;
            this.maxReadCount = maxReadCount;
        }

        public boolean isDownsample() {
            return downsample;
        }

        public int getSampleWindowSize() {
            return sampleWindowSize;
        }

        public int getMaxReadCount() {
            return maxReadCount;
        }

    }
}

