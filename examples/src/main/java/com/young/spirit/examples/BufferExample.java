package com.young.spirit.examples;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BufferExample {
    public static void main(String[] args) {
        /**
         * position is zero
         */
        ByteBuffer byteBuffer = ByteBuffer.wrap("hello,young".getBytes(StandardCharsets.UTF_8));
//        System.out.println(byteBuffer);
//        byteBuffer.flip();
//        System.out.println(byteBuffer);
//        byteBuffer.put(". nio".getBytes(StandardCharsets.UTF_8));
        System.out.println(byteBuffer);
        /**
         * position don't move
         */
        System.out.println(new String(byteBuffer.array()));
        System.out.println(byteBuffer);

        /**
         * remaining
         */
        if(byteBuffer.hasRemaining()) {
            System.out.println("has remaining");
        }
    }
}
