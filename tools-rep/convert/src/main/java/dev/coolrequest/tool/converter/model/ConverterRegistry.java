package dev.coolrequest.tool.converter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConverterRegistry {
    private final List<Format> formats = new ArrayList<>();
    private final Map<String, Format> formatById = new HashMap<>();
    private final Map<String, Map<String, Converter>> graph = new HashMap<>();
    private final Map<String, String> pivotByFormatId = new HashMap<>();

    public void registerFormat(Format format) {
        if (!formatById.containsKey(format.getId())) {
            formats.add(format);
            formatById.put(format.getId(), format);
        }
    }

    public void registerConverter(Format from, Format to, Converter converter) {
        registerFormat(from);
        registerFormat(to);
        graph.computeIfAbsent(from.getId(), k -> new HashMap<>()).put(to.getId(), converter);
    }

    public void setPivot(Format format, Format pivot) {
        pivotByFormatId.put(format.getId(), pivot.getId());
    }

    public Converter findConverter(Format from, Format to) {
        if (from.getId().equals(to.getId())) {
            return input -> input;
        }
        Map<String, Converter> fromMap = graph.get(from.getId());
        if (fromMap != null) {
            Converter direct = fromMap.get(to.getId());
            if (direct != null) return direct;
        }
        String pivotId = pivotByFormatId.get(from.getId());
        if (pivotId != null && pivotId.equals(pivotByFormatId.get(to.getId()))) {
            Format pivot = formatById.get(pivotId);
            if (pivot != null) {
                Converter toPivot = fromMap == null ? null : fromMap.get(pivotId);
                Map<String, Converter> pivotMap = graph.get(pivotId);
                Converter fromPivot = pivotMap == null ? null : pivotMap.get(to.getId());
                if (toPivot != null && fromPivot != null) {
                    final Converter c1 = toPivot;
                    final Converter c2 = fromPivot;
                    return input -> c2.convert(c1.convert(input));
                }
            }
        }
        return null;
    }

    public List<Format> getAllFormats() {
        return Collections.unmodifiableList(formats);
    }

    public Format getFormatById(String id) {
        return formatById.get(id);
    }
}
