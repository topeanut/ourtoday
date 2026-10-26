// 앱 아이콘 생성 (App Store 는 알파 채널 금지 → 생성 후 JPEG 왕복으로 알파 제거):
//   swift scripts/make-icon.swift /tmp/icon.png && sips -s format jpeg /tmp/icon.png --out /tmp/icon.jpg && sips -s format png /tmp/icon.jpg --out OurToday/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
import AppKit

let size = 1024.0
let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "AppIcon-1024.png"
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size), pixelsHigh: Int(size),
                           bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let ctx = NSGraphicsContext.current!.cgContext

func color(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

// 배경 (아이콘 모서리는 iOS 가 자동으로 둥글게 자름 → 알파 없이 꽉 채움)
ctx.setFillColor(color(0xFFF1EC))
ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))

// 좌표: Android 벡터(108 캔버스)와 같은 디자인을 1024 로 확대, y 축 뒤집기
let s = size / 108.0
func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * s, y: size - y * s) }

// 해
ctx.setFillColor(color(0xFFC27A))
ctx.fillEllipse(in: CGRect(x: (70 - 7) * s, y: size - (36 + 7) * s, width: 14 * s, height: 14 * s))

// 하트
let heart = CGMutablePath()
heart.move(to: p(54, 76))
heart.addCurve(to: p(34, 49), control1: p(54, 76), control2: p(34, 63))
heart.addCurve(to: p(45.5, 37), control1: p(34, 42), control2: p(39.5, 37))
heart.addCurve(to: p(54, 42.3), control1: p(49.5, 37), control2: p(52.5, 39.2))
heart.addCurve(to: p(62.5, 37), control1: p(55.5, 39.2), control2: p(58.5, 37))
heart.addCurve(to: p(74, 49), control1: p(68.5, 37), control2: p(74, 42))
heart.addCurve(to: p(54, 76), control1: p(74, 63), control2: p(54, 76))
heart.closeSubpath()
ctx.addPath(heart)
ctx.setFillColor(color(0xFF7A8A))
ctx.fillPath()

// 하이라이트
ctx.setFillColor(color(0xFFFFFF, 0.55))
ctx.fillEllipse(in: CGRect(x: (42 - 3) * s, y: size - (47 + 3) * s, width: 6 * s, height: 6 * s))

NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print("wrote \(out)")
