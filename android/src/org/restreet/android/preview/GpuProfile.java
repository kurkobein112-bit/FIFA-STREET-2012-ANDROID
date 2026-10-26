package org.restreet.android.preview;

public enum GpuProfile {
    MALI("Mali · compatibilidad limitada", false),
    ADRENO("Snapdragon / Adreno · experimental, sin validar", true);

    public final String label;
    public final boolean requireVertexStores;
    GpuProfile(String label, boolean requireVertexStores) {
        this.label = label;
        this.requireVertexStores = requireVertexStores;
    }
    @Override public String toString() { return label; }
    public String vertexStoresArgument() {
        return "--vulkan_require_vertex_pipeline_stores_and_atomics=" + requireVertexStores;
    }
    public static GpuProfile fromName(String value) {
        for (GpuProfile profile : values()) if (profile.name().equals(value)) return profile;
        return MALI;
    }
}
