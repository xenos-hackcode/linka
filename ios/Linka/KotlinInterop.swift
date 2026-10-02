import Foundation
import LinkaCore

/// Kotlin `ByteArray` crosses into Swift as `KotlinByteArray`, not `Data` - this is the
/// standard, well-documented conversion (unlike the sealed-class interop risk flagged
/// elsewhere in this codebase, ByteArray<->Data is one of the most common, reliable
/// patterns in Kotlin/Native<->Swift interop).
extension KotlinByteArray {
    func toData() -> Data {
        let length = Int(size)
        var data = Data(capacity: length)
        for i in 0..<length {
            data.append(UInt8(bitPattern: get(index: Int32(i))))
        }
        return data
    }
}
