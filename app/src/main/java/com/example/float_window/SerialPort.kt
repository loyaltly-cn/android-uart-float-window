package com.example.float_window

class SerialPort {
    companion object {
        init { System.loadLibrary("serial_port") }

        @JvmStatic
        external fun open(path: String, baudrate: Int, flags: Int): java.io.FileDescriptor?

        @JvmStatic
        external fun close()
    }
}