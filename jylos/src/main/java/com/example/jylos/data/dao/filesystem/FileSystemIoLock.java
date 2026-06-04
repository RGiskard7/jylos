package com.example.jylos.data.dao.filesystem;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared lock for mutating filesystem DAO operations.
 * Keeps note/folder/tag writes and cache refreshes serialized.
 */
final class FileSystemIoLock {
    static final ReentrantLock LOCK = new ReentrantLock(true);

    private FileSystemIoLock() {
    }
}
