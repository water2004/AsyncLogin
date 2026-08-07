package org.edtp.asynclogin.core;

@FunctionalInterface
public interface CheckedSupplier<T> {
    T get() throws Exception;
}
