package raccoonman.reterraforged.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Forge production uses the stable SRG name m_142416_ for Screen's widget
 * registration method. Keeping this invoker out of the refmap prevents the
 * missing named-method mapping that previously failed during client startup.
 */
@Pseudo
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker(value = "m_142416_", remap = false)
    <T extends GuiEventListener & Renderable & NarratableEntry> T invokeAddRenderableWidget(T widget);
}
