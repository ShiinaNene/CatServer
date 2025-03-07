/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSpecBuilder;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static net.minecraftforge.fml.loading.LogMarkers.CORE;

public class FMLServiceProvider implements ITransformationService
{

    private static final Logger LOGGER = LogManager.getLogger();
    private ArgumentAcceptingOptionSpec<String> modsOption;
    private ArgumentAcceptingOptionSpec<String> modListsOption;
    private ArgumentAcceptingOptionSpec<String> mavenRootsOption;
    private ArgumentAcceptingOptionSpec<String> forgeOption;
    private ArgumentAcceptingOptionSpec<String> mcOption;
    private ArgumentAcceptingOptionSpec<String> forgeGroupOption;
    private ArgumentAcceptingOptionSpec<String> mcpOption;
    private ArgumentAcceptingOptionSpec<String> mappingsOption;
    private List<String> modsArgumentList;
    private List<String> modListsArgumentList;
    private List<String> mavenRootsArgumentList;
    private String targetForgeVersion;
    private String targetMcVersion;
    private String targetMcpVersion;
    private String targetMcpMappings;
    private String targetForgeGroup;
    private Map<String, Object> arguments;

    @Override
    public String name()
    {
        return "fml";
    }

    @Override
    public void initialize(IEnvironment environment) {
        LOGGER.debug(CORE, "Setting up basic FML game directories");
        FMLPaths.setup(environment);
        LOGGER.debug(CORE, "Loading configuration");
        FMLConfig.load();
        LOGGER.debug(CORE, "Preparing ModFile");
        environment.computePropertyIfAbsent(Environment.Keys.MODFILEFACTORY.get(), k->ModFile.buildFactory());
        arguments = new HashMap<>();
        arguments.put("modLists", modListsArgumentList);
        arguments.put("mods", modsArgumentList);
        arguments.put("mavenRoots", mavenRootsArgumentList);
        arguments.put("forgeVersion", targetForgeVersion);
        arguments.put("forgeGroup", targetForgeGroup);
        arguments.put("mcVersion", targetMcVersion);
        arguments.put("mcpVersion", targetMcpVersion);
        arguments.put("mcpMappings", targetMcpMappings);
        LOGGER.debug(CORE, "Preparing launch handler");
        FMLLoader.setupLaunchHandler(environment, arguments);
        FMLEnvironment.setupInteropEnvironment(environment);
        Environment.build(environment);
    }

    @Override
    public void beginScanning(final IEnvironment environment) {
        throw new IllegalStateException("WHY ARE YOU HERE??????");
    }

    @Override
    public List<Map.Entry<String, Path>> runScan(final IEnvironment environment) {
        LOGGER.debug(CORE,"Initiating mod scan");
        final Map<IModFile.Type, List<ModFile>> foundFiles = FMLLoader.beginModScan(arguments);
        return foundFiles
                .values()
                .stream()
                .flatMap(Collection::stream)
                .map(modFile -> new AbstractMap.SimpleImmutableEntry<>(modFile.getFileName(), modFile.getFilePath()))
                .collect(Collectors.toList());
    }

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException
    {
        LOGGER.debug("Injecting tracing printstreams for STDOUT/STDERR.");
        System.setOut(new TracingPrintStream(LogManager.getLogger("STDOUT"), System.out));
        System.setErr(new TracingPrintStream(LogManager.getLogger("STDERR"), System.err));
        FMLLoader.onInitialLoad(environment, otherServices);
    }

    @Override
    public void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder)
    {
        forgeOption = argumentBuilder.apply("forgeVersion", "Forge Version number").withRequiredArg().ofType(String.class).required();
        forgeGroupOption = argumentBuilder.apply("forgeGroup", "Forge Group (for testing)").withRequiredArg().ofType(String.class).defaultsTo("net.minecraftforge");
        mcOption = argumentBuilder.apply("mcVersion", "Minecraft Version number").withRequiredArg().ofType(String.class).required();
        mcpOption = argumentBuilder.apply("mcpVersion", "MCP Version number").withRequiredArg().ofType(String.class).required();
        mappingsOption = argumentBuilder.apply("mcpMappings", "MCP Mappings Channel and Version").withRequiredArg().ofType(String.class);
        modsOption = argumentBuilder.apply("mods", "List of mods to add").withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
        modListsOption = argumentBuilder.apply("modLists", "JSON modlists").withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
        mavenRootsOption = argumentBuilder.apply("mavenRoots", "Maven root directories").withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
    }

    @Override
    public void argumentValues(OptionResult option)
    {
        modsArgumentList = option.values(modsOption);
        modListsArgumentList = option.values(modListsOption);
        mavenRootsArgumentList = option.values(mavenRootsOption);
        targetForgeVersion = option.value(forgeOption);
        targetForgeGroup = option.value(forgeGroupOption);
        targetMcVersion = option.value(mcOption);
        targetMcpVersion = option.value(mcpOption);
        targetMcpMappings = option.value(mappingsOption);
    }

    @Nonnull
    @Override
    public List<ITransformer> transformers()
    {
        LOGGER.debug(CORE, "Loading coremod transformers");
        // CatServer start
        List<ITransformer> transformers = new ArrayList<>();
        transformers.addAll(catserver.server.coremod.CoreMod.getTransformers());
        transformers.addAll(FMLLoader.getCoreModProvider().getCoreModTransformers());
        return transformers;
        // CatServer end
    }

}
