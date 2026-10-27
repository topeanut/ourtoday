// 랜딩 이미지 생성: OG(1200x630), 파비콘(32/180), 임시 스크린샷(1080x1920 x4)
// 실행: swift scripts/make-images.swift  (landing/ 에서)
import Foundation
import CoreGraphics
import CoreText
import ImageIO
import UniformTypeIdentifiers

let brand = CGColor(red: 1, green: 0x7A/255, blue: 0x8A/255, alpha: 1)
let brandStrong = CGColor(red: 0xF2/255, green: 0x56/255, blue: 0x6B/255, alpha: 1)
let soft = CGColor(red: 1, green: 0xE3/255, blue: 0xE7/255, alpha: 1)
let cream = CGColor(red: 1, green: 0xF8/255, blue: 0xF5/255, alpha: 1)
let ink = CGColor(red: 0x2B/255, green: 0x23/255, blue: 0x26/255, alpha: 1)
let sub = CGColor(red: 0x8A/255, green: 0x7E/255, blue: 0x82/255, alpha: 1)
let white = CGColor(red: 1, green: 1, blue: 1, alpha: 1)
let sun = CGColor(red: 1, green: 0xF3/255, blue: 0xC4/255, alpha: 1)

func context(_ w: Int, _ h: Int) -> CGContext {
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    // 위가 0 인 좌표계로
    ctx.translateBy(x: 0, y: CGFloat(h)); ctx.scaleBy(x: 1, y: -1)
    return ctx
}

func save(_ ctx: CGContext, _ path: String) {
    let img = ctx.makeImage()!
    let url = URL(fileURLWithPath: path) as CFURL
    let dest = CGImageDestinationCreateWithURL(url, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, img, nil)
    CGImageDestinationFinalize(dest)
}

func roundRect(_ ctx: CGContext, _ r: CGRect, _ radius: CGFloat, _ color: CGColor) {
    ctx.setFillColor(color)
    ctx.addPath(CGPath(roundedRect: r, cornerWidth: radius, cornerHeight: radius, transform: nil))
    ctx.fillPath()
}

func text(_ ctx: CGContext, _ s: String, x: CGFloat, y: CGFloat, size: CGFloat, color: CGColor, bold: Bool = false, maxWidth: CGFloat = 2000) {
    let fontName = bold ? "AppleSDGothicNeo-Bold" : "AppleSDGothicNeo-Regular"
    let font = CTFontCreateWithName(fontName as CFString, size, nil)
    let attrs: [NSAttributedString.Key: Any] = [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
    ]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: s, attributes: attrs))
    ctx.saveGState()
    // 텍스트는 원래 좌표계(아래가 0)로 그려야 뒤집히지 않음
    ctx.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
    ctx.textPosition = CGPoint(x: x, y: y + size)
    CTLineDraw(line, ctx)
    ctx.restoreGState()
}

/// 브랜드 아이콘: 둥근 사각형 + 해 + 하트
func icon(_ ctx: CGContext, _ r: CGRect) {
    let s = r.width
    roundRect(ctx, r, s * 0.22, brand)
    ctx.setFillColor(sun)
    ctx.fillEllipse(in: CGRect(x: r.minX + s * 0.375, y: r.minY + s * 0.26, width: s * 0.25, height: s * 0.25))
    // 하트 (두 원 + 삼각형)
    ctx.setFillColor(white)
    let cx = r.midX, top = r.minY + s * 0.42, hw = s * 0.27
    let rr = hw / 2
    ctx.fillEllipse(in: CGRect(x: cx - hw, y: top, width: rr * 2, height: rr * 2))
    ctx.fillEllipse(in: CGRect(x: cx, y: top, width: rr * 2, height: rr * 2))
    ctx.beginPath()
    ctx.move(to: CGPoint(x: cx - hw + rr * 0.15, y: top + rr * 1.35))
    ctx.addLine(to: CGPoint(x: cx + hw - rr * 0.15, y: top + rr * 1.35))
    ctx.addLine(to: CGPoint(x: cx, y: top + hw * 1.75))
    ctx.closePath(); ctx.fillPath()
}

let out = FileManager.default.currentDirectoryPath + "/public"

// 파비콘
for (size, name) in [(32, "favicon-32.png"), (180, "apple-touch-icon.png"), (512, "icon-512.png")] {
    let c = context(size, size)
    if name == "apple-touch-icon.png" {
        // iOS 는 투명 영역을 검게 칠하므로 꽉 채움
        c.setFillColor(brand); c.fill(CGRect(x: 0, y: 0, width: size, height: size))
        icon(c, CGRect(x: 0, y: 0, width: size, height: size))
    } else {
        icon(c, CGRect(x: 0, y: 0, width: size, height: size))
    }
    save(c, "\(out)/\(name)")
}

// OG 1200x630
do {
    let c = context(1200, 630)
    c.setFillColor(cream); c.fill(CGRect(x: 0, y: 0, width: 1200, height: 630))
    roundRect(c, CGRect(x: 760, y: -80, width: 560, height: 560), 280, soft)
    icon(c, CGRect(x: 80, y: 90, width: 120, height: 120))
    text(c, "오늘의 우리", x: 80, y: 250, size: 84, color: ink, bold: true)
    text(c, "세 줄이면 충분한 커플 일기", x: 80, y: 360, size: 44, color: brandStrong, bold: true)
    text(c, "작년 오늘 · 데이트 버킷리스트 · 커플 캘린더", x: 80, y: 440, size: 32, color: sub)
    // 오른쪽 휴대폰 목업
    roundRect(c, CGRect(x: 850, y: 110, width: 250, height: 470), 36, ink)
    roundRect(c, CGRect(x: 862, y: 122, width: 226, height: 446), 28, cream)
    roundRect(c, CGRect(x: 878, y: 150, width: 194, height: 120), 18, soft)
    roundRect(c, CGRect(x: 878, y: 286, width: 194, height: 120), 18, white)
    roundRect(c, CGRect(x: 878, y: 422, width: 194, height: 120), 18, white)
    save(c, "\(out)/og.png")
}

// 임시 스크린샷 1080x1920 (실제 앱 화면으로 교체 예정)
let screens: [(String, String, [String])] = [
    ("홈", "오늘의 기록", ["퇴근길에 같이 붕어빵 사 먹었다.", "팥이 꼬리까지 꽉 차 있었음", "내일도 저 가게 가자"]),
    ("작년 오늘", "1년 전 오늘", ["처음으로 같이 한강 피크닉 🌿", "돗자리 깔자마자 바람이…", "추억 2개 다시 보기"]),
    ("버킷리스트", "함께 이룬 것 2 / 3", ["오로라 보러 가기", "같이 마라톤 10km 완주", "한강 피크닉 ✓"]),
    ("캘린더", "10월 26일", ["1주년 기념일 🎂", "부산 여행", "치과 예약"]),
]
for (i, s) in screens.enumerated() {
    let c = context(1080, 1920)
    c.setFillColor(cream); c.fill(CGRect(x: 0, y: 0, width: 1080, height: 1920))
    text(c, s.0, x: 72, y: 150, size: 64, color: ink, bold: true)
    roundRect(c, CGRect(x: 60, y: 300, width: 960, height: 420), 56, i == 1 ? soft : white)
    text(c, s.1, x: 120, y: 360, size: 44, color: brandStrong, bold: true)
    for (j, line) in s.2.enumerated() {
        text(c, line, x: 120, y: 460 + CGFloat(j) * 72, size: 42, color: ink)
    }
    for k in 0..<3 {
        roundRect(c, CGRect(x: 60, y: 780 + CGFloat(k) * 300, width: 960, height: 260), 56, white)
        roundRect(c, CGRect(x: 120, y: 840 + CGFloat(k) * 300, width: 520, height: 36), 18, soft)
        roundRect(c, CGRect(x: 120, y: 900 + CGFloat(k) * 300, width: 760, height: 28), 14, CGColor(red: 0.95, green: 0.92, blue: 0.91, alpha: 1))
    }
    c.setFillColor(white); c.fill(CGRect(x: 0, y: 1740, width: 1080, height: 180))
    for k in 0..<4 {
        roundRect(c, CGRect(x: 120 + CGFloat(k) * 240, y: 1790, width: 64, height: 64), 18, k == i ? brand : soft)
    }
    save(c, "\(out)/screens/\(i + 1).png")
}
print("done")
