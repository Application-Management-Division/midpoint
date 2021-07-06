/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.task.quartzimpl;

import com.evolveum.midpoint.task.api.TaskHandler;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Maintains registered task handlers.
 *
 * TODO finish review of this class
 */
@Component
public class TaskHandlerRegistry {

    private static final Trace LOGGER = TraceManager.getTrace(TaskHandlerRegistry.class);

    /** Task handlers mapped from their URIs. */
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Primary handlers URIs.
     * These will be taken into account when searching for handler matching a given task category.
     */
    private final Map<String, TaskHandler> primaryHandlersUris = new ConcurrentHashMap<>();

    /** All non-deprecated handlers URIs. */
    private final Map<String, TaskHandler> nonDeprecatedHandlersUris = new ConcurrentHashMap<>();

    /**
     * Handler URI to be used if no URI is specified in the task.
     */
    private String defaultHandlerUri;

    public void registerHandler(@NotNull String uri, @NotNull TaskHandler handler) {
        LOGGER.trace("Registering task handler for URI {}", uri);
        handlers.put(uri, handler);
        nonDeprecatedHandlersUris.put(uri, handler);
        primaryHandlersUris.put(uri, handler);
    }

    void unregisterHandler(@NotNull String uri) {
        handlers.remove(uri);
        nonDeprecatedHandlersUris.remove(uri);
        primaryHandlersUris.remove(uri);
    }

    void registerAdditionalHandlerUri(@NotNull String uri, @NotNull TaskHandler handler) {
        LOGGER.trace("Registering additional URI for a task handler: {}", uri);
        nonDeprecatedHandlersUris.put(uri, handler);
        handlers.put(uri, handler);
    }

    void registerDeprecatedHandlerUri(@NotNull String uri, @NotNull TaskHandler handler) {
        LOGGER.trace("Registering additional (deprecated) URI for a task handler: {}", uri);
        handlers.put(uri, handler);
    }

    public TaskHandler getHandler(String uri) {
        if (uri != null) {
            return handlers.get(uri);
        } else {
            return null;
        }
    }

    @Deprecated // Remove in 4.2
    public List<String> getAllTaskCategories() {
        Set<String> categories = new HashSet<>();
        for (TaskHandler h : primaryHandlersUris.values()) {
            List<String> cat = h.getCategoryNames();
            if (cat != null) {
                categories.addAll(cat);
            } else {
                String catName = h.getCategoryName(null);
                if (catName != null) {
                    categories.add(catName);
                }
            }
        }
        return new ArrayList<>(categories);
    }

    @Deprecated // Remove in 4.2
    public String getHandlerUriForCategory(String category) {
        Set<String> found = new HashSet<>();
        for (Map.Entry<String, TaskHandler> h : primaryHandlersUris.entrySet()) {
            List<String> cats = h.getValue().getCategoryNames();
            if (cats != null) {
                if (cats.contains(category)) {
                    found.add(h.getKey());
                }
            } else {
                String cat = h.getValue().getCategoryName(null);
                if (category.equals(cat)) {
                    found.add(h.getKey());
                }
            }
        }
        if (found.isEmpty()) {
            return null;
        } else if (found.size() == 1) {
            return found.iterator().next();
        } else {
            LOGGER.warn("More task handlers found for category {}; returning none.", category);
            return null;
        }
    }

    public Collection<String> getAllHandlerUris(boolean nonDeprecatedOnly) {
        return Collections.unmodifiableSet(getHandlerUriMap(nonDeprecatedOnly).keySet());
    }

    private Map<String, TaskHandler> getHandlerUriMap(boolean nonDeprecatedOnly) {
        return nonDeprecatedOnly ? nonDeprecatedHandlersUris : handlers;
    }

    public Collection<String> getHandlerUrisForArchetype(String archetypeOid, boolean nonDeprecatedOnly) {
        return getHandlerUriMap(nonDeprecatedOnly).entrySet().stream()
                .filter(entry -> archetypeOid.equals(entry.getValue().getArchetypeOid(entry.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public String getDefaultHandlerUri() {
        return defaultHandlerUri;
    }

    public void setDefaultHandlerUri(String defaultHandlerUri) {
        this.defaultHandlerUri = defaultHandlerUri;
    }
}
