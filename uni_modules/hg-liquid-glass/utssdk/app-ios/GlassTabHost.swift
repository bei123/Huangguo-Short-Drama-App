import UIKit

/// Apple Music 风格悬浮液态玻璃 TabBar：胶囊毛玻璃 + 选中图标圆底高亮。
@objc public final class GlassTabHost: NSObject {
	public static let shared = GlassTabHost()

	private let titles = ["首页", "发现", "搜索", "我的"]
	private let iconNames = ["hg_tab_home", "hg_tab_cate", "hg_tab_search", "hg_tab_mine"]
	private let activeIconNames = ["hg_tab_home_active", "hg_tab_cate_active", "hg_tab_search_active", "hg_tab_mine_active"]
	/// 黄果香槟金（对应 Music 选中蓝）
	private let accent = UIColor(red: 232 / 255, green: 194 / 255, blue: 122 / 255, alpha: 1)
	private let idle = UIColor(white: 0.72, alpha: 1)

	private weak var container: UIView?
	private var items: [TabItemView] = []
	private var selectedIndex: Int = 0
	private var suppressCallback = false
	private var onSelect: ((Int) -> Void)?
	private var attachAttempts = 0

	@objc public func show(_ onSelect: @escaping (Int) -> Void) {
		self.onSelect = onSelect
		DispatchQueue.main.async {
			self.attachIfNeeded()
		}
	}

	@objc public func setIndex(_ index: Int) {
		DispatchQueue.main.async {
			self.suppressCallback = true
			self.applySelection(index, animated: false)
			self.suppressCallback = false
		}
	}

	@objc public func hide() {
		DispatchQueue.main.async {
			self.container?.removeFromSuperview()
			self.container = nil
			self.items.removeAll()
			self.onSelect = nil
			self.attachAttempts = 0
		}
	}

	private func attachIfNeeded() {
		if let existing = container, existing.superview != nil {
			existing.superview?.bringSubviewToFront(existing)
			return
		}
		guard let host = Self.topWindow() else {
			attachAttempts += 1
			if attachAttempts < 10 {
				DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
					self?.attachIfNeeded()
				}
			} else {
				print("[hg-lg-ios] no key window after retries")
			}
			return
		}

		let bar = makeBar()
		host.addSubview(bar)
		NSLayoutConstraint.activate([
			bar.leadingAnchor.constraint(equalTo: host.safeAreaLayoutGuide.leadingAnchor, constant: 18),
			bar.trailingAnchor.constraint(equalTo: host.safeAreaLayoutGuide.trailingAnchor, constant: -18),
			bar.bottomAnchor.constraint(equalTo: host.safeAreaLayoutGuide.bottomAnchor, constant: -8),
			bar.heightAnchor.constraint(equalToConstant: 62),
		])
		host.bringSubviewToFront(bar)
		container = bar
		print("[hg-lg-ios] Music-style tabbar attached")
	}

	private func makeBar() -> UIView {
		// 外层负责圆角阴影，内层玻璃
		let shadow = UIView()
		shadow.translatesAutoresizingMaskIntoConstraints = false
		shadow.backgroundColor = .clear
		shadow.layer.shadowColor = UIColor.black.cgColor
		shadow.layer.shadowOpacity = 0.28
		shadow.layer.shadowRadius = 18
		shadow.layer.shadowOffset = CGSize(width: 0, height: 8)

		let wrap = UIView()
		wrap.translatesAutoresizingMaskIntoConstraints = false
		wrap.layer.cornerRadius = 31
		wrap.layer.cornerCurve = .continuous
		wrap.clipsToBounds = true
		shadow.addSubview(wrap)
		NSLayoutConstraint.activate([
			wrap.leadingAnchor.constraint(equalTo: shadow.leadingAnchor),
			wrap.trailingAnchor.constraint(equalTo: shadow.trailingAnchor),
			wrap.topAnchor.constraint(equalTo: shadow.topAnchor),
			wrap.bottomAnchor.constraint(equalTo: shadow.bottomAnchor),
		])

		let glass = makeGlassView()
		glass.translatesAutoresizingMaskIntoConstraints = false
		wrap.addSubview(glass)
		NSLayoutConstraint.activate([
			glass.leadingAnchor.constraint(equalTo: wrap.leadingAnchor),
			glass.trailingAnchor.constraint(equalTo: wrap.trailingAnchor),
			glass.topAnchor.constraint(equalTo: wrap.topAnchor),
			glass.bottomAnchor.constraint(equalTo: wrap.bottomAnchor),
		])

		// 细描边，接近 Music 玻璃边缘
		let border = UIView()
		border.translatesAutoresizingMaskIntoConstraints = false
		border.isUserInteractionEnabled = false
		border.backgroundColor = .clear
		border.layer.cornerRadius = 31
		border.layer.cornerCurve = .continuous
		border.layer.borderWidth = 0.5
		border.layer.borderColor = UIColor.white.withAlphaComponent(0.18).cgColor
		wrap.addSubview(border)
		NSLayoutConstraint.activate([
			border.leadingAnchor.constraint(equalTo: wrap.leadingAnchor),
			border.trailingAnchor.constraint(equalTo: wrap.trailingAnchor),
			border.topAnchor.constraint(equalTo: wrap.topAnchor),
			border.bottomAnchor.constraint(equalTo: wrap.bottomAnchor),
		])

		let stack = UIStackView()
		stack.translatesAutoresizingMaskIntoConstraints = false
		stack.axis = .horizontal
		stack.distribution = .fillEqually
		stack.alignment = .fill
		wrap.addSubview(stack)
		NSLayoutConstraint.activate([
			stack.leadingAnchor.constraint(equalTo: wrap.leadingAnchor, constant: 8),
			stack.trailingAnchor.constraint(equalTo: wrap.trailingAnchor, constant: -8),
			stack.topAnchor.constraint(equalTo: wrap.topAnchor, constant: 6),
			stack.bottomAnchor.constraint(equalTo: wrap.bottomAnchor, constant: -6),
		])

		items.removeAll()
		for i in 0..<titles.count {
			let item = TabItemView(
				title: titles[i],
				idleImage: Self.bundleImage(named: iconNames[i]),
				activeImage: Self.bundleImage(named: activeIconNames[i]),
				accent: accent,
				idle: idle
			)
			item.tag = i
			item.addTarget(self, action: #selector(tabTapped(_:)), for: .touchUpInside)
			items.append(item)
			stack.addArrangedSubview(item)
		}
		applySelection(selectedIndex, animated: false)
		return shadow
	}

	private func makeGlassView() -> UIView {
		if #available(iOS 26.0, *) {
			// Clear 更接近 Music：能透出背后内容
			let effect = UIGlassEffect(style: .clear)
			effect.isInteractive = true
			effect.tintColor = UIColor(white: 0.15, alpha: 0.22)
			let view = UIVisualEffectView(effect: effect)
			view.layer.cornerRadius = 31
			view.layer.cornerCurve = .continuous
			view.clipsToBounds = true
			return view
		}
		let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterialDark))
		blur.layer.cornerRadius = 31
		blur.layer.cornerCurve = .continuous
		blur.clipsToBounds = true
		let veil = UIView()
		veil.translatesAutoresizingMaskIntoConstraints = false
		veil.backgroundColor = UIColor(white: 0.12, alpha: 0.28)
		blur.contentView.addSubview(veil)
		NSLayoutConstraint.activate([
			veil.leadingAnchor.constraint(equalTo: blur.contentView.leadingAnchor),
			veil.trailingAnchor.constraint(equalTo: blur.contentView.trailingAnchor),
			veil.topAnchor.constraint(equalTo: blur.contentView.topAnchor),
			veil.bottomAnchor.constraint(equalTo: blur.contentView.bottomAnchor),
		])
		return blur
	}

	@objc private func tabTapped(_ sender: UIControl) {
		let index = sender.tag
		applySelection(index, animated: true)
		guard !suppressCallback else { return }
		let cb = onSelect
		DispatchQueue.main.async { cb?(index) }
	}

	private func applySelection(_ index: Int, animated: Bool) {
		guard index >= 0, index < items.count else { return }
		selectedIndex = index
		for (i, item) in items.enumerated() {
			item.setSelected(i == index, animated: animated)
		}
	}

	private static func bundleImage(named: String) -> UIImage? {
		let bundle = Bundle(for: GlassTabHost.self)
		if let img = UIImage(named: named, in: bundle, compatibleWith: nil) {
			return img.withRenderingMode(.alwaysTemplate)
		}
		if let url = bundle.url(forResource: named, withExtension: "png") {
			return UIImage(contentsOfFile: url.path)?.withRenderingMode(.alwaysTemplate)
		}
		return UIImage(named: named)?.withRenderingMode(.alwaysTemplate)
	}

	private static func topWindow() -> UIWindow? {
		let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
		let windows = scenes.flatMap { $0.windows }
		return windows.last { $0.isKeyWindow && !$0.isHidden }
			?? windows.last { !$0.isHidden }
			?? windows.last
	}
}

// MARK: - Tab item (Music: 图标下有圆底高亮，文字在下方)

private final class TabItemView: UIControl {
	private let iconBubble = UIView()
	private let iconView = UIImageView()
	private let titleLabel = UILabel()
	private let idleImage: UIImage?
	private let activeImage: UIImage?
	private let accent: UIColor
	private let idle: UIColor
	private var isItemSelected = false

	init(title: String, idleImage: UIImage?, activeImage: UIImage?, accent: UIColor, idle: UIColor) {
		self.idleImage = idleImage
		self.activeImage = activeImage
		self.accent = accent
		self.idle = idle
		super.init(frame: .zero)
		translatesAutoresizingMaskIntoConstraints = false
		isAccessibilityElement = true
		accessibilityTraits = .button
		accessibilityLabel = title

		iconBubble.translatesAutoresizingMaskIntoConstraints = false
		iconBubble.layer.cornerRadius = 16
		iconBubble.layer.cornerCurve = .continuous
		iconBubble.isUserInteractionEnabled = false

		iconView.translatesAutoresizingMaskIntoConstraints = false
		iconView.contentMode = .scaleAspectFit
		iconView.isUserInteractionEnabled = false

		titleLabel.translatesAutoresizingMaskIntoConstraints = false
		titleLabel.text = title
		titleLabel.font = .systemFont(ofSize: 10, weight: .medium)
		titleLabel.textAlignment = .center
		titleLabel.isUserInteractionEnabled = false

		addSubview(iconBubble)
		iconBubble.addSubview(iconView)
		addSubview(titleLabel)

		NSLayoutConstraint.activate([
			iconBubble.topAnchor.constraint(equalTo: topAnchor, constant: 2),
			iconBubble.centerXAnchor.constraint(equalTo: centerXAnchor),
			iconBubble.widthAnchor.constraint(equalToConstant: 44),
			iconBubble.heightAnchor.constraint(equalToConstant: 32),

			iconView.centerXAnchor.constraint(equalTo: iconBubble.centerXAnchor),
			iconView.centerYAnchor.constraint(equalTo: iconBubble.centerYAnchor),
			iconView.widthAnchor.constraint(equalToConstant: 22),
			iconView.heightAnchor.constraint(equalToConstant: 22),

			titleLabel.topAnchor.constraint(equalTo: iconBubble.bottomAnchor, constant: 2),
			titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor),
			titleLabel.trailingAnchor.constraint(equalTo: trailingAnchor),
			titleLabel.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor),
		])
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

	func setSelected(_ selected: Bool, animated: Bool) {
		isItemSelected = selected
		let apply = {
			self.iconBubble.backgroundColor = selected
				? UIColor.white.withAlphaComponent(0.16)
				: .clear
			self.iconView.image = selected ? (self.activeImage ?? self.idleImage) : self.idleImage
			self.iconView.tintColor = selected ? self.accent : self.idle
			self.titleLabel.textColor = selected ? self.accent : self.idle
			self.accessibilityTraits = selected ? [.button, .selected] : .button
		}
		if animated {
			UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseInOut]) { apply() }
		} else {
			apply()
		}
	}

	override var isHighlighted: Bool {
		didSet {
			alpha = isHighlighted ? 0.7 : 1
		}
	}
}
