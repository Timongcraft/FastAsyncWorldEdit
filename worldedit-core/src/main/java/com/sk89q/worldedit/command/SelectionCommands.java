/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.fastasyncworldedit.core.configuration.Caption;
import com.fastasyncworldedit.core.extent.clipboard.URIClipboardHolder;
import com.fastasyncworldedit.core.function.mask.IdMask;
import com.fastasyncworldedit.core.regions.selector.FuzzyRegionSelector;
import com.fastasyncworldedit.core.regions.selector.PolyhedralRegionSelector;
import com.fastasyncworldedit.core.util.MaskTraverser;
import com.google.common.base.Strings;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.command.argument.SelectorChoice;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.SelectionWand;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Locatable;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.block.BlockDistributionCounter;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.internal.annotation.Direction;
import com.sk89q.worldedit.internal.annotation.MultiDirection;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.CylinderRegionSelector;
import com.sk89q.worldedit.regions.selector.EllipsoidRegionSelector;
import com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldedit.regions.selector.RegionSelectorType;
import com.sk89q.worldedit.regions.selector.SphereRegionSelector;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.component.CommandListBox;
import com.sk89q.worldedit.util.formatting.component.InvalidComponentException;
import com.sk89q.worldedit.util.formatting.component.PaginationBox;
import com.sk89q.worldedit.util.formatting.component.SubtleFormat;
import com.sk89q.worldedit.util.formatting.component.TextComponentProducer;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.storage.ChunkStore;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;
import org.enginehub.piston.exception.StopExecutionException;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.sk89q.worldedit.command.util.Logging.LogMode.POSITION;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.world.storage.ChunkStore.CHUNK_SHIFTS;
import static com.sk89q.worldedit.world.storage.ChunkStore.CHUNK_SHIFTS_Y;

/**
 * Selection commands.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class SelectionCommands {

    private final WorldEdit we;

    public SelectionCommands(WorldEdit we) {
        this.we = we;
    }

    @Command(
            name = "/pos1",
            aliases = "/1",
            desc = "Set position 1"
    )
    @Logging(POSITION)
    @CommandPermissions("worldedit.selection.pos")
    public void pos1(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Coordinates to set position 1 to", def = "")
                    BlockVector3 coordinates
    ) throws WorldEditException {
        Location pos;
        //FAWE start - clamp
        if (coordinates != null) {
            pos = new Location(world, coordinates.toVector3().clampY(world.getMinY(), world.getMaxY()));
        } else if (actor instanceof Locatable) {
            pos = ((Locatable) actor).getBlockLocation().clampY(world.getMinY(), world.getMaxY());
            //FAWE end
        } else {
            actor.print(Caption.of("worldedit.pos.console-require-coords"));
            return;
        }

        if (!session.getRegionSelector(world).selectPrimary(pos.toVector().toBlockPoint(), ActorSelectorLimits.forActor(actor))) {
            actor.print(Caption.of("worldedit.pos.already-set"));
            return;
        }

        session.getRegionSelector(world)
                .explainPrimarySelection(actor, session, pos.toVector().toBlockPoint());
    }

    @Command(
            name = "/pos2",
            aliases = "/2",
            desc = "Set position 2"
    )
    @Logging(POSITION)
    @CommandPermissions("worldedit.selection.pos")
    public void pos2(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Coordinates to set position 2 to", def = "")
                    BlockVector3 coordinates
    ) throws WorldEditException {
        Location pos;
        if (coordinates != null) {
            //FAWE start - clamp
            pos = new Location(world, coordinates.toVector3().clampY(world.getMinY(), world.getMaxY()));
        } else if (actor instanceof Locatable) {
            pos = ((Locatable) actor).getBlockLocation().clampY(world.getMinY(), world.getMaxY());
            //Fawe end
        } else {
            actor.print(Caption.of("worldedit.pos.console-require-coords"));
            return;
        }

        if (!session.getRegionSelector(world).selectSecondary(
                pos.toVector().toBlockPoint(),
                ActorSelectorLimits.forActor(actor)
        )) {
            actor.print(Caption.of("worldedit.pos.already-set"));
            return;
        }

        session.getRegionSelector(world)
                .explainSecondarySelection(actor, session, pos.toVector().toBlockPoint());
    }

    @Command(
            name = "/hpos1",
            desc = "Set position 1 to targeted block"
    )
    @CommandPermissions("worldedit.selection.hpos")
    public void hpos1(Player player, LocalSession session) throws WorldEditException {

        Location pos = player.getBlockTrace(300);

        if (pos != null) {
            if (!session.getRegionSelector(player.getWorld()).selectPrimary(
                    pos.toVector().toBlockPoint(),
                    ActorSelectorLimits.forActor(player)
            )) {
                player.print(Caption.of("worldedit.hpos.already-set"));
                return;
            }

            session.getRegionSelector(player.getWorld())
                    .explainPrimarySelection(player, session, pos.toBlockPoint());
        } else {
            player.print(Caption.of("worldedit.hpos.no-block"));
        }
    }

    @Command(
            name = "/hpos2",
            desc = "Set position 2 to targeted block"
    )
    @CommandPermissions("worldedit.selection.hpos")
    public void hpos2(Player player, LocalSession session) throws WorldEditException {

        Location pos = player.getBlockTrace(300);

        if (pos != null) {
            if (!session.getRegionSelector(player.getWorld()).selectSecondary(
                    pos.toVector().toBlockPoint(),
                    ActorSelectorLimits.forActor(player)
            )) {
                player.print(Caption.of("worldedit.hpos.already-set"));
                return;
            }

            session.getRegionSelector(player.getWorld())
                    .explainSecondarySelection(player, session, pos.toBlockPoint());
        } else {
            player.print(Caption.of("worldedit.hpos.no-block"));
        }
    }

    @Command(
            name = "/chunk",
            desc = "Set the selection to your current chunk.",
            descFooter = "This command selects 256-block-tall areas,\nwhich can be specified by the y-coordinate.\nE.g. -c x,1,z will select from y=256 to y=511."
    )
    @Logging(POSITION)
    @CommandPermissions("worldedit.selection.chunk")
    public void chunk(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "The chunk to select", def = "")
                    BlockVector3 coordinates,
            @Switch(name = 's', desc = "Expand your selection to encompass all chunks that are part of it")
                    boolean expandSelection,
            @Switch(name = 'c', desc = "Use chunk coordinates instead of block coordinates")
                    boolean useChunkCoordinates
    ) throws WorldEditException {
        final BlockVector3 min;
        final BlockVector3 max;
        if (expandSelection) {
            Region region = session.getSelection(world);

            int minChunkY = world.getMinY() >> CHUNK_SHIFTS_Y;
            int maxChunkY = world.getMaxY() >> CHUNK_SHIFTS_Y;

            BlockVector3 minChunk = ChunkStore.toChunk3d(region.getMinimumPoint())
                    .clampY(minChunkY, maxChunkY);
            BlockVector3 maxChunk = ChunkStore.toChunk3d(region.getMaximumPoint())
                    .clampY(minChunkY, maxChunkY);

            min = minChunk.shl(CHUNK_SHIFTS, CHUNK_SHIFTS_Y, CHUNK_SHIFTS);
            max = maxChunk.shl(CHUNK_SHIFTS, CHUNK_SHIFTS_Y, CHUNK_SHIFTS).add(15, 255, 15);

            actor.print(Caption.of(
                    "worldedit.chunk.selected-multiple",
                    TextComponent.of(minChunk.x()),
                    TextComponent.of(minChunk.y()),
                    TextComponent.of(minChunk.z()),
                    TextComponent.of(maxChunk.x()),
                    TextComponent.of(maxChunk.y()),
                    TextComponent.of(maxChunk.z())
            ));
        } else {
            BlockVector3 minChunk;
            if (coordinates != null) {
                // coords specified
                minChunk = useChunkCoordinates
                        ? coordinates
                        : ChunkStore.toChunk3d(coordinates);
            } else {
                // use player loc
                if (actor instanceof Locatable) {
                    minChunk = ChunkStore.toChunk3d(((Locatable) actor).getBlockLocation().toVector().toBlockPoint());
                } else {
                    throw new StopExecutionException(TextComponent.of("A player or coordinates are required."));
                }
            }

            min = minChunk.shl(CHUNK_SHIFTS, CHUNK_SHIFTS_Y, CHUNK_SHIFTS);
            max = min.add(15, 255, 15);

            actor.print(Caption.of(
                    "worldedit.chunk.selected",
                    TextComponent.of(minChunk.x()),
                    TextComponent.of(minChunk.y()),
                    TextComponent.of(minChunk.z())
            ));
        }

        final CuboidRegionSelector selector;
        if (session.getRegionSelector(world) instanceof ExtendingCuboidRegionSelector) {
            selector = new ExtendingCuboidRegionSelector(world);
        } else {
            selector = new CuboidRegionSelector(world);
        }
        selector.selectPrimary(min, ActorSelectorLimits.forActor(actor));
        selector.selectSecondary(max, ActorSelectorLimits.forActor(actor));
        session.setRegionSelector(world, selector);

        session.dispatchCUISelection(actor);

    }

    @Command(
            name = "/wand",
            desc = "Get the wand object"
    )
    @CommandPermissions(
            value = "worldedit.wand",
            queued = false
    )
    public void wand(
            Player player, LocalSession session,
            @Switch(name = 'n', desc = "Get a navigation wand") boolean navWand
    ) throws WorldEditException {
        //FAWE start
        session.loadDefaults(player, true);
        //FAWE end
        BaseItem wand = navWand ? session.getNavWandBaseItem() : session.getWandBaseItem();
        if (wand == null) {
            String wandId = navWand ? we.getConfiguration().navigationWand : we.getConfiguration().wandItem;
            //FAWE start - allow item NBT
            ParserContext parserContext = new ParserContext();
            parserContext.setActor(player);
            parserContext.setSession(session);
            try {
                wand = WorldEdit.getInstance().getItemFactory().parseFromInput(wandId, parserContext);
            } catch (InputParseException e) {
                player.print(Caption.of("worldedit.wand.invalid"));
                return;
            }
        }
        player.giveItem(new BaseItemStack(wand.getType(), wand.getNbtReference(), 1));
        //FAWE end
        //FAWE start - instance-iate session
        if (navWand) {
            session.setTool(wand, NavigationWand.INSTANCE);
            player.print(Caption.of("worldedit.wand.navwand.info"));
        } else {
            session.setTool(wand, SelectionWand.INSTANCE);
            player.print(Caption.of("worldedit.wand.selwand.info"));
            //FAWE end
        }
    }

    @Command(
            name = "toggleeditwand",
            aliases = {"/toggleeditwand"},
            desc = "Remind the user that the wand is now a tool and can be unbound with /tool none."
    )
    @CommandPermissions(
            value = "worldedit.wand.toggle",
            queued = false
    )
    public void toggleWand(Player player) {
        player.print(
                Caption.of(
                        "worldedit.wand.selwand.now.tool",
                        TextComponent.of("/tool none").clickEvent(
                                ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "/tool none")),
                        TextComponent.of("/tool selwand").clickEvent(
                                ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "/tool selwand")),
                        TextComponent.of("//wand").clickEvent(
                                ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "//wand"))
                )
        );
    }

    @Command(
            name = "/contract",
            desc = "Contract the selection area"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.selection.contract")
    public void contract(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Amount to contract the selection by")
                    int amount,
            @Arg(desc = "Amount to contract the selection by in the other direction", def = "0")
                    int reverseAmount,
            @Arg(desc = "Direction to contract", def = Direction.AIM)
            @MultiDirection
                    List<BlockVector3> direction
    ) throws WorldEditException {
        try {
            Region region = session.getSelection(world);
            long oldSize = region.getVolume();
            if (reverseAmount == 0) {
                for (BlockVector3 dir : direction) {
                    region.contract(dir.multiply(amount));
                }
            } else {
                for (BlockVector3 dir : direction) {
                    region.contract(dir.multiply(amount), dir.multiply(-reverseAmount));
                }
            }
            session.getRegionSelector(world).learnChanges();
            long newSize = region.getVolume();

            session.getRegionSelector(world).explainRegionAdjust(actor, session);

            actor.print(Caption.of("worldedit.contract.contracted", TextComponent.of(oldSize - newSize)));
        } catch (RegionOperationException e) {
            actor.printError(TextComponent.of(e.getMessage()));
        }
    }

    @Command(
            name = "/shift",
            desc = "Shift the selection area"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.selection.shift")
    public void shift(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Amount to shift the selection by")
                    int amount,
            @Arg(desc = "Direction to contract", def = Direction.AIM)
            @MultiDirection
                    List<BlockVector3> direction
    ) throws WorldEditException {
        try {
            Region region = session.getSelection(world);

            for (BlockVector3 dir : direction) {
                region.shift(dir.multiply(amount));
            }

            session.getRegionSelector(world).learnChanges();

            session.getRegionSelector(world).explainRegionAdjust(actor, session);

            actor.print(Caption.of("worldedit.shift.shifted"));
        } catch (RegionOperationException e) {
            actor.printError(TextComponent.of(e.getMessage()));
        }
    }

    @Command(
            name = "/outset",
            desc = "Outset the selection area"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.selection.outset")
    public void outset(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Amount to expand the selection by in all directions")
                    int amount,
            @Switch(name = 'h', desc = "Only expand horizontally")
                    boolean onlyHorizontal,
            @Switch(name = 'v', desc = "Only expand vertically")
                    boolean onlyVertical
    ) throws WorldEditException {
        Region region = session.getSelection(world);
        region.expand(getChangesForEachDir(amount, onlyHorizontal, onlyVertical));
        session.getRegionSelector(world).learnChanges();
        session.getRegionSelector(world).explainRegionAdjust(actor, session);
        actor.print(Caption.of("worldedit.outset.outset"));
    }

    @Command(
            name = "/inset",
            desc = "Inset the selection area"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.selection.inset")
    public void inset(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Amount to contract the selection by in all directions")
                    int amount,
            @Switch(name = 'h', desc = "Only contract horizontally")
                    boolean onlyHorizontal,
            @Switch(name = 'v', desc = "Only contract vertically")
                    boolean onlyVertical
    ) throws WorldEditException {
        Region region = session.getSelection(world);
        region.contract(getChangesForEachDir(amount, onlyHorizontal, onlyVertical));
        session.getRegionSelector(world).learnChanges();
        session.getRegionSelector(world).explainRegionAdjust(actor, session);
        actor.print(Caption.of("worldedit.inset.inset"));
    }

    private BlockVector3[] getChangesForEachDir(int amount, boolean onlyHorizontal, boolean onlyVertical) {
        Stream.Builder<BlockVector3> changes = Stream.builder();

        if (!onlyHorizontal) {
            changes.add(BlockVector3.UNIT_Y);
            changes.add(BlockVector3.UNIT_MINUS_Y);
        }

        if (!onlyVertical) {
            changes.add(BlockVector3.UNIT_X);
            changes.add(BlockVector3.UNIT_MINUS_X);
            changes.add(BlockVector3.UNIT_Z);
            changes.add(BlockVector3.UNIT_MINUS_Z);
        }

        return changes.build().map(v -> v.multiply(amount)).toArray(BlockVector3[]::new);
    }

    @Command(
            name = "/trim",
            desc = "Minimize the selection to encompass matching blocks"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.selection.trim")
    public void trim(Actor actor, World world, LocalSession session,
                     @Arg(desc = "Mask of blocks to keep within the selection", def = "#existing")
                         Mask mask) throws WorldEditException {
        // Avoid checking blocks outside the original region but within the cuboid region
        Region originalRegion = session.getSelection(world);
        if (!(originalRegion instanceof CuboidRegion)) {
            mask = new MaskIntersection(new RegionMask(originalRegion), mask);
        }

        // Result region will be cuboid
        CuboidRegion region = originalRegion.getBoundingBox();

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int minY = 0;
        boolean found = false;

        outer: for (int y = min.y(); y <= max.y(); y++) {
            for (int x = min.x(); x <= max.x(); x++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    BlockVector3 vec = BlockVector3.at(x, y, z);

                    if (mask.test(vec)) {
                        found = true;
                        minY = y;

                        break outer;
                    }
                }
            }
        }

        // If anything was found in the first pass, then the remaining variables are guaranteed to be set
        if (!found) {
            throw new StopExecutionException(Caption.of("worldedit.trim.no-blocks"));
        }

        int maxY = minY;

        outer: for (int y = max.y(); y > minY; y--) {
            for (int x = min.x(); x <= max.x(); x++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    BlockVector3 vec = BlockVector3.at(x, y, z);

                    if (mask.test(vec)) {
                        maxY = y;
                        break outer;
                    }
                }
            }
        }

        int minX = 0;

        outer: for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockVector3 vec = BlockVector3.at(x, y, z);

                    if (mask.test(vec)) {
                        minX = x;
                        break outer;
                    }
                }
            }
        }

        int maxX = minX;

        outer: for (int x = max.x(); x > minX; x--) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockVector3 vec = BlockVector3.at(x, y, z);

                    if (mask.test(vec)) {
                        maxX = x;
                        break outer;
                    }
                }
            }
        }

        int minZ = 0;

        outer: for (int z = min.z(); z <= max.z(); z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockVector3 vec = BlockVector3.at(x, y, z);

                    if (mask.test(vec)) {
                        minZ = z;
                        break outer;
                    }
                }
            }
        }

        int maxZ = minZ;

        outer: for (int z = max.z(); z > minZ; z--) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockVector3 vec = BlockVector3.at(x, y, z);

                    if (mask.test(vec)) {
                        maxZ = z;
                        break outer;
                    }
                }
            }
        }

        final CuboidRegionSelector selector;
        if (session.getRegionSelector(world) instanceof ExtendingCuboidRegionSelector) {
            selector = new ExtendingCuboidRegionSelector(world, BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
        } else {
            selector = new CuboidRegionSelector(world, BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
        }
        session.setRegionSelector(world, selector);

        session.getRegionSelector(world).learnChanges();
        session.getRegionSelector(world).explainRegionAdjust(actor, session);
        actor.print(Caption.of("worldedit.trim.trim"));
    }

    @Command(
            name = "/size",
            desc = "Get information about the selection"
    )
    @CommandPermissions(
            value = "worldedit.selection.size",
            queued = false
    )
    public void size(
            Actor actor, World world, LocalSession session,
            @Switch(name = 'c', desc = "Get clipboard info instead")
                    boolean clipboardInfo
    ) throws WorldEditException {
        Region region;
        if (clipboardInfo) {
            //FAWE start - Modify for cross server clipboards
            ClipboardHolder root = session.getClipboard();
            int index = 0;
            for (ClipboardHolder holder : root.getHolders()) {
                Clipboard clipboard = holder.getClipboard();
                String name;
                if (holder instanceof URIClipboardHolder) {
                    URI uri = ((URIClipboardHolder) holder).getUri();
                    if (uri.toString().startsWith("file:/")) {
                        name = new File(uri.getPath()).getName();
                    } else {
                        name = uri.getFragment();
                    }
                } else {
                    name = Integer.toString(index);
                }

                region = clipboard.getRegion();
                BlockVector3 size = region.getMaximumPoint()
                        .subtract(region.getMinimumPoint()).add(1, 1, 1);
                BlockVector3 origin = clipboard.getOrigin();

                String sizeStr = size.x() + "*" + size.y() + "*" + size.z();
                String originStr = origin.x() + "," + origin.y() + "," + origin.z();

                long numBlocks = ((long) size.x() * size.y() * size.z());
                actor.print(Caption.of(
                        "worldedit.size.offset",
                        TextComponent.of(name),
                        TextComponent.of(sizeStr),
                        TextComponent.of(originStr),
                        TextComponent.of(numBlocks)
                ));
                index++;
            }
            return;
            //FAWE end
        } else {
            region = session.getSelection(world);

            actor.print(Caption.of("worldedit.size.type", TextComponent.of(session.getRegionSelector(world).getTypeName())));

            for (Component line : session.getRegionSelector(world).getSelectionInfoLines()) {
                actor.printInfo(line);
            }
        }
        BlockVector3 size = region.getMaximumPoint()
                .subtract(region.getMinimumPoint())
                .add(1, 1, 1);

        actor.print(Caption.of("worldedit.size.size", TextComponent.of(size.toString())));
        actor.print(Caption.of(
                "worldedit.size.distance",
                TextComponent.of(region.getMaximumPoint().distance(region.getMinimumPoint()))
        ));
        actor.print(Caption.of("worldedit.size.blocks", TextComponent.of(region.getVolume())));
    }

    @Command(
            name = "/count",
            desc = "Counts the number of blocks matching a mask"
    )
    @CommandPermissions("worldedit.analysis.count")
    public int count(
            Actor actor, World world, LocalSession session, EditSession editSession,
            @Arg(desc = "The mask of blocks to match")
                    Mask mask
    ) throws WorldEditException {
        //FAWE start > the mask will have been initialised with a WorldWrapper extent (very bad/slow)
        new MaskTraverser(mask).setNewExtent(editSession);
        //FAWE end
        int count = editSession.countBlocks(session.getSelection(world), mask);
        actor.print(Caption.of("worldedit.count.counted", TextComponent.of(count)));
        return count;
    }

    @Command(
            name = "/distr",
            desc = "Get the distribution of blocks in the selection"
    )
    @CommandPermissions("worldedit.analysis.distr")
    public void distr(
            Actor actor, World world, LocalSession session,
            //FAWE start > add extent to RegionVisitor to allow chunk preloading
            EditSession editSession,
            //FAWE end
            @Switch(name = 'c', desc = "Get the distribution of the clipboard instead")
                    boolean clipboardDistr,
            @Switch(name = 'd', desc = "Separate blocks by state")
                    boolean separateStates,
            @ArgFlag(name = 'p', desc = "Gets page from a previous distribution.")
                    Integer page
    ) throws WorldEditException {
        List<Countable<BlockState>> distribution;

        if (page == null) {
            if (clipboardDistr) {
                Clipboard clipboard = session.getClipboard().getClipboard(); // throws if missing
                BlockDistributionCounter count = new BlockDistributionCounter(clipboard, separateStates);
                //FAWE start > add extent to RegionVisitor to allow chunk preloading
                RegionVisitor visitor = new RegionVisitor(clipboard.getRegion(), count, editSession);
                //FAWE end
                Operations.completeBlindly(visitor);
                distribution = count.getDistribution();
            } else {
                distribution = editSession.getBlockDistribution(session.getSelection(world), separateStates);
            }
            session.setLastDistribution(distribution);
            page = 1;
        } else {
            distribution = session.getLastDistribution();
            if (distribution == null) {
                actor.print(Caption.of("worldedit.distr.no-previous"));
                return;
            }
        }

        if (distribution.isEmpty()) {  // *Should* always be false
            actor.print(Caption.of("worldedit.distr.no-blocks"));
            return;
        }

        BlockDistributionResult res = new BlockDistributionResult(distribution, separateStates);
        if (!actor.isPlayer()) {
            res.formatForConsole();
        }
        actor.print(res.create(page));
    }

    @Command(
            name = "/sel",
            aliases = {";", "/desel", "/deselect"},
            desc = "Choose a region selector"
    )
    //FAWE start
    @CommandPermissions("worldedit.analysis.sel")
    //FAWE end
    public void select(
            Actor actor, World world, LocalSession session,
            @Arg(desc = "Selector to switch to", def = "")
                    SelectorChoice selector,
            @Switch(name = 'd', desc = "Set default selector")
                    boolean setDefaultSelector
    ) throws WorldEditException {
        if (selector == null) {
            session.getRegionSelector(world).clear();
            session.dispatchCUISelection(actor);
            actor.print(Caption.of("worldedit.select.cleared"));
            return;
        }

        final RegionSelector oldSelector = session.getRegionSelector(world);

        final RegionSelector newSelector;
        switch (selector) {
            case CUBOID:
                newSelector = new CuboidRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.cuboid.message"));
                break;
            case EXTEND:
                newSelector = new ExtendingCuboidRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.extend.message"));
                break;
            case POLY: {
                newSelector = new Polygonal2DRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.poly.message"));
                Optional<Integer> limit = ActorSelectorLimits.forActor(actor).getPolygonVertexLimit();
                limit.ifPresent(integer -> actor.print(Caption.of(
                        "worldedit.select.poly.limit-message",
                        TextComponent.of(integer)
                )));
                break;
            }
            case ELLIPSOID:
                newSelector = new EllipsoidRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.ellipsoid.message"));
                break;
            case SPHERE:
                newSelector = new SphereRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.sphere.message"));
                break;
            case CYL:
                newSelector = new CylinderRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.cyl.message"));
                break;
            case CONVEX:
            case HULL:
            case POLYHEDRON: {
                newSelector = new ConvexPolyhedralRegionSelector(oldSelector);
                actor.print(Caption.of("worldedit.select.convex.message"));
                Optional<Integer> limit = ActorSelectorLimits.forActor(actor).getPolyhedronVertexLimit();
                limit.ifPresent(integer -> actor.print(Caption.of(
                        "worldedit.select.convex.limit-message",
                        TextComponent.of(integer)
                )));
                break;
            }
            //FAWE start
            case POLYHEDRAL:
                newSelector = new PolyhedralRegionSelector(world);
                actor.print(Caption.of("fawe.selection.sel.convex.polyhedral"));
                Optional<Integer> limit = ActorSelectorLimits.forActor(actor).getPolyhedronVertexLimit();
                limit.ifPresent(integer -> actor.print(Caption.of("fawe.selection.sel.max", integer)));
                actor.print(Caption.of("fawe.selection.sel.list"));
                break;
            case FUZZY:
            case MAGIC:
                Mask maskOpt = new IdMask(world);
                newSelector = new FuzzyRegionSelector(actor, world, maskOpt);
                actor.print(Caption.of("fawe.selection.sel.fuzzy"));
                actor.print(Caption.of("fawe.selection.sel.list"));
                break;
            //FAWE end
            case LIST:
            default:
                CommandListBox box = new CommandListBox("Selection modes", null, null);
                box.setHidingHelp(true);
                TextComponentProducer contents = box.getContents();
                contents.append(SubtleFormat.wrap("Select one of the modes below:")).newline();

                box.appendCommand("cuboid", Caption.of("worldedit.select.cuboid.description"), "//sel cuboid");
                box.appendCommand("extend", Caption.of("worldedit.select.extend.description"), "//sel extend");
                box.appendCommand("poly", Caption.of("worldedit.select.poly.description"), "//sel poly");
                box.appendCommand("ellipsoid", Caption.of("worldedit.select.ellipsoid.description"), "//sel ellipsoid");
                box.appendCommand("sphere", Caption.of("worldedit.select.sphere.description"), "//sel sphere");
                box.appendCommand("cyl", Caption.of("worldedit.select.cyl.description"), "//sel cyl");
                box.appendCommand("convex", Caption.of("worldedit.select.convex.description"), "//sel convex");

                //FAWE start
                box.appendCommand("polyhedral", Caption.of("fawe.selection.sel.polyhedral"), "//sel polyhedral");
                box.appendCommand("fuzzy[=<mask>]", Caption.of("fawe.selection.sel.fuzzy-instruction"), "//sel fuzzy[=<mask>]");
                box.setComponentsPerPage(box.getComponentsSize());
                //FAWE end

                actor.print(box.create(1));
                return;
        }

        if (setDefaultSelector) {
            RegionSelectorType found = RegionSelectorType.getForSelector(newSelector);

            if (found != null) {
                session.setDefaultRegionSelector(found);
                actor.print(Caption.of("worldedit.select.default-set", TextComponent.of(found.name())));
            } else {
                throw new RuntimeException("Something unexpected happened. Please report this.");
            }
        }

        session.setRegionSelector(world, newSelector);
        session.dispatchCUISelection(actor);
    }

    public static class BlockDistributionResult extends PaginationBox {

        private final List<Countable<BlockState>> distribution;
        private final int totalBlocks;
        private final boolean separateStates;
        //FAWE start
        private final int maxDigits;
        private boolean consoleFormat = false;
        //FAWE end

        public BlockDistributionResult(List<Countable<BlockState>> distribution, boolean separateStates) {
            this(distribution, separateStates, "//distr -p %page%" + (separateStates ? " -d" : ""));
        }

        public BlockDistributionResult(List<Countable<BlockState>> distribution, boolean separateStates, String pageCommand) {
            super("Block Distribution", pageCommand);
            this.distribution = distribution;
            // note: doing things like region.getArea is inaccurate for non-cuboids.
            this.totalBlocks = distribution.stream().mapToInt(Countable::getAmount).sum();
            this.separateStates = separateStates;
            setComponentsPerPage(7);
            //FAWE start
            this.maxDigits = (int) (Math.log10(distribution.get(0).getAmount()) + 1);
            //FAWE end
        }

        @Override
        public Component getComponent(int number) {
            Countable<BlockState> c = distribution.get(number);
            TextComponent.Builder line = TextComponent.builder();

            final int count = c.getAmount();

            //FAWE start - better formatting, support console
            final double perc = count / (double) totalBlocks * 100;
            final int curDigits = (int) (Math.log10(count) + 1);
            // Assume console uses monospaced font
            final String space = consoleFormat ? " " : "  ";

            line.append(String.format("%s%.3f%%  ", perc < 10 ? space : "", perc), TextColor.GOLD);
            final int diff = maxDigits - curDigits;
            final int multipler;
            if (consoleFormat) {
                multipler = diff + 2;
            } else {
                multipler = diff == 0 ? 2 : 2 * diff + 1;
            }
            String pad = Strings.repeat(" ", multipler);
            //FAWE end
            line.append(String.format("%s%s", count, pad), TextColor.YELLOW);

            final BlockState state = c.getID();
            final BlockType blockType = state.getBlockType();

            //FAWE start - better formatting, support console; Translation keys will not work on console
            Component blockName = consoleFormat ? TextComponent.of(blockType.getName()) : blockType.getRichName();
            //FAWE end

            TextComponent toolTip;
            if (separateStates && state != blockType.getDefaultState()) {
                toolTip = TextComponent.of(state.getAsString());
                blockName = blockName.append(TextComponent.of("*"));
            } else {
                toolTip = TextComponent.of(blockType.id());
            }
            blockName = blockName.hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, toolTip));
            line.append(blockName);

            return line.build();
        }

        @Override
        public int getComponentsSize() {
            return distribution.size();
        }

        //FAWE start - support for console
        @Override
        public void formatForConsole() {
            this.consoleFormat = true;
            super.formatForConsole();
        }
        //FAWE end

        @Override
        public Component create(int page) throws InvalidComponentException {
            super.getContents().append(Caption.of("worldedit.distr.total", TextComponent.of(totalBlocks)))
                    .append(TextComponent.newline());
            return super.create(page);
        }

    }

}
