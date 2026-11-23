import UIKit

/// 读取系统 Dynamic Type 正文字号，供 uni-app-x 按比例缩放界面文字。
@objc public class FontMetrics: NSObject {
	/// 当前系统 body 文本样式的 pointSize（默认约为 17）
	@objc public static func bodyPointSize() -> Double {
		return Double(UIFont.preferredFont(forTextStyle: .body).pointSize)
	}
}
