/* Original development harness. All Rights Reserved. */
package baseline.reproduction.performance;

@net.minecraftforge.fml.common.Mod("task6b_performance")
public final class HeadlessBenchmark {
    public HeadlessBenchmark() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(this::gather);
    }
    private void gather(net.minecraftforge.data.event.GatherDataEvent event) {
            try {
                WorldgenBenchmark.main(new String[]{System.getProperty("task6b.output")});
                System.exit(0);
            } catch(Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
    }
}
