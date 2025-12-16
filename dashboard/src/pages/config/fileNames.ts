export interface FileNameWithExt {
    name: string;
    ext: string;
}

export function getFileNameWithExt(fileName: string): FileNameWithExt {
    const index = fileName.lastIndexOf('.');
    if (index < 0) {
        return {
            name: fileName,
            ext: ''
        };
    }
    return {
        name: fileName.substring(0, index),
        ext: fileName.substring(index + 1)
    };
}

export function getFullFileName(name: string, ext: string): string {
    return `${name}.${ext}`;
}