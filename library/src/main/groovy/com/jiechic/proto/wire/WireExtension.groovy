package com.jiechic.proto.wire


class WireExtension {
    boolean noOptions = false;
    boolean android = false;
    List<String> enumOptions = new ArrayList<>()


    void setNoOptions(boolean noOptions) {
        this.noOptions = noOptions
    }

    public boolean getNoOptions() {
        return noOptions
    }

    public void setAndroid(boolean android) {
        this.android = android
    }

    public boolean getAndroid() {
        return android
    }

    void setEnumOptions(List<String> enumOptions) {
        this.enumOptions = enumOptions
    }

    public Collection<String> getEnumOptions() {
        return enumOptions
    }

    int hashCode() {
        int result
        result = (noOptions ? 1 : 0)
        result = 31 * result + (android ? 1 : 0)
        result = 31 * result + enumOptions.hashCode()
        return result
    }
}