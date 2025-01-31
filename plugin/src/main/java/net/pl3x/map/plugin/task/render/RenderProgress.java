package net.pl3x.map.plugin.task.render;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.pl3x.map.plugin.Logger;
import net.pl3x.map.plugin.configuration.Lang;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RenderProgress extends TimerTask {

    private final DecimalFormat dfPercent = new DecimalFormat("0.00%");
    private final DecimalFormat dfRate = new DecimalFormat("0.0");
    private final AbstractRender render;
    private final long startTime;

    final int[] rollingAvgCps = new int[15];
    final List<Integer> totalAvgCps = new ArrayList<>();
    int index = 0;
    int prevChunks;

    private RenderProgress(final @NonNull AbstractRender render) {
        this.startTime = System.currentTimeMillis();
        this.render = render;
        this.prevChunks = this.render.processedChunks();
    }

    public static Timer printProgress(final @NonNull AbstractRender render) {
        final RenderProgress progress = new RenderProgress(render);
        final Timer timer = new Timer();
        final int interval = render.mapWorld.config().MAP_RENDER_PROGRESS_INTERVAL;
        if (interval > 0) {
            timer.scheduleAtFixedRate(progress, interval * 1000L, interval * 1000L);
        }
        return timer;
    }

    @Override
    public void run() {
        if (render.mapWorld.rendersPaused()) {
            return;
        }

        final int curChunks = this.render.processedChunks();
        final int diff = curChunks - prevChunks;
        prevChunks = curChunks;

        rollingAvgCps[index] = diff;
        totalAvgCps.add(diff);
        index++;
        if (index == 15) {
            index = 0;
        }
        final double rollingAvg = Arrays.stream(rollingAvgCps).filter(i -> i != 0).average().orElse(0.00D);

        final int chunksLeft = this.render.totalChunks() - curChunks;
        final long timeLeft = (long) (chunksLeft / (totalAvgCps.stream().filter(i -> i != 0).mapToInt(i -> i).average().orElse(0.00D) / 1000));

        String etaStr = formatMilliseconds(timeLeft);
        String elapsedStr = formatMilliseconds(System.currentTimeMillis() - startTime);

        double percent = (double) curChunks / (double) this.render.totalChunks();

        String rateStr = dfRate.format(rollingAvg);
        String percentStr = dfPercent.format(percent);

        int curRegions = this.render.processedRegions();
        int totalRegions = this.render.totalRegions();

        Logger.info(
                (totalRegions > 0 ? Lang.LOG_RENDER_PROGRESS_WITH_REGIONS : Lang.LOG_RENDER_PROGRESS),
                Placeholder.unparsed("world", render.world.getName()),
                Placeholder.unparsed("current_regions", Integer.toString(curRegions)),
                Placeholder.unparsed("total_regions", Integer.toString(totalRegions)),
                Placeholder.unparsed("current_chunks", Integer.toString(curChunks)),
                Placeholder.unparsed("total_chunks", Integer.toString(this.render.totalChunks())),
                Placeholder.unparsed("percent", percentStr),
                Placeholder.unparsed("elapsed", elapsedStr),
                Placeholder.unparsed("eta", etaStr),
                Placeholder.unparsed("rate", rateStr)
        );

    }

    private static @NonNull String formatMilliseconds(long timeLeft) {
        int hrs = (int) TimeUnit.MILLISECONDS.toHours(timeLeft) % 24;
        int min = (int) TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60;
        int sec = (int) TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60;
        return String.format("%02d:%02d:%02d", hrs, min, sec);
    }
}
