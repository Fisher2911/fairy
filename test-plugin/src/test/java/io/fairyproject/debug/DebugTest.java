package io.fairyproject.debug;

import be.seeseemelk.mockbukkit.Coordinate;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import io.fairyproject.tests.bukkit.BukkitAssert;
import io.fairyproject.tests.bukkit.BukkitTestingBase;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.Assert;
import org.junit.Test;

public class DebugTest extends BukkitTestingBase {

    @Test
    public void testBlockBreak() {
        final PlayerMock player = SERVER.addPlayer();

        final WorldMock world = SERVER.addSimpleWorld("world");

        final BlockBreakEvent event = player.simulateBlockBreak(world.createBlock(new Coordinate()));
        Assert.assertNotNull(event);
        BukkitAssert.assertCancelled(event);
    }

}