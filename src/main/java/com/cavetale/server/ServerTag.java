package com.cavetale.server;

import com.cavetale.core.util.Json;
import com.cavetale.mytems.Mytems;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Stored on disk in the plugin data folder and on Redis.
 */
@Data
public final class ServerTag {
    protected String name;
    protected boolean persistent;
    protected int priority;
    protected boolean locked;
    protected boolean hidden;
    protected String displayName;
    protected List<String> description;
    protected String material;

    public Component parseDisplayName() {
        return displayName != null
            ? Json.deserializeComponent(displayName)
            : Component.empty();
    }

    public List<Component> parseDescription() {
        if (description == null) return Collections.emptyList();
        return description.stream()
            .map(Json::deserializeComponent)
            .collect(Collectors.toList());
    }

    public ItemStack parseItemStack() {
        if (material == null) return new ItemStack(Material.STONE);
        ItemStack item;
        item = Mytems.deserializeItem(material);
        if (item != null) return item;
        try {
            Material mat = Material.valueOf(material.toUpperCase());
            if (!mat.isEmpty()) return new ItemStack(mat);
        } catch (IllegalArgumentException iae) { }
        return new ItemStack(Material.STONE);
    }

    public static ServerTag load(File file) {
        return Json.load(file, ServerTag.class, ServerTag::new);
    }

    public void save(File file) {
        Json.save(file, this, true);
    }

    public String toJson() {
        return Json.serialize(this);
    }

    public String prettyPrint() {
        return Json.prettyPrint(this);
    }

    public static ServerTag fromJson(String json) {
        return Json.deserialize(json, ServerTag.class);
    }
}
