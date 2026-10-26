import UIKit

/// 메인 페이지를 못 불러왔을 때 보여주는 화면
final class ErrorView: UIView {

    var onRetry: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = AppConfig.background

        let title = UILabel()
        title.text = "연결할 수 없어요"
        title.font = .systemFont(ofSize: 20, weight: .bold)
        title.textColor = AppConfig.textPrimary

        let message = UILabel()
        message.text = "인터넷 연결을 확인한 뒤 다시 시도해 주세요."
        message.font = .systemFont(ofSize: 15)
        message.textColor = AppConfig.textSecondary
        message.numberOfLines = 0
        message.textAlignment = .center

        var buttonConfig = UIButton.Configuration.filled()
        buttonConfig.title = "다시 시도"
        buttonConfig.baseBackgroundColor = AppConfig.brand
        buttonConfig.baseForegroundColor = .white
        buttonConfig.cornerStyle = .large
        buttonConfig.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 32, bottom: 12, trailing: 32)
        buttonConfig.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attrs in
            var attrs = attrs
            attrs.font = .systemFont(ofSize: 16, weight: .semibold)
            return attrs
        }
        let button = UIButton(configuration: buttonConfig)
        button.addTarget(self, action: #selector(retry), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [title, message, button])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 8
        stack.setCustomSpacing(24, after: message)
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerYAnchor.constraint(equalTo: centerYAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 32),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -32),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    @objc private func retry() {
        onRetry?()
    }
}
