package me.ahoo.cosky.rest.util;

import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author ahoo wang
 */
public final class Zips {

    @SneakyThrows
    public static byte[] zip(List<ZipItem> items) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (ZipItem item : items) {
                zipOutputStream.putNextEntry(new ZipEntry(item.getName()));
                zipOutputStream.write(item.getData().getBytes(StandardCharsets.UTF_8));
            }
            zipOutputStream.flush();
            zipOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        }
    }

    @SneakyThrows
    public static List<ZipItem> unzip(byte[] zipSource) {
        List<ZipItem> items = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipSource))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                try (ByteArrayOutputStream itemOutputStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int offset;
                    while ((offset = zipInputStream.read(buffer)) != -1) {
                        itemOutputStream.write(buffer, 0, offset);
                    }
                    String entryName = entry.getName();
                    items.add(ZipItem.of(entryName, itemOutputStream.toString("UTF-8")));
                }
            }
            return items;
        }
    }


    public static class ZipItem {
        private final String name;
        private final String data;

        public ZipItem(String name, String data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public String getData() {
            return data;
        }

        public static ZipItem of(String name, String data) {
            return new ZipItem(name, data);
        }
    }

}
