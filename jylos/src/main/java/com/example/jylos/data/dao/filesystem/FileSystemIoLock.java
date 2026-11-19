package com.example.jylos.data.dao.filesystem;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared lock for mutating filesystem DAO operations.
 * Keeps note/folder/tag writes and cache refreshes serialized.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
final class FileSystemIoLock {
    static final ReentrantLock LOCK = new ReentrantLock(true);

    private FileSystemIoLock() {
    }
}
