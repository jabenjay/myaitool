import SwiftUI
import AVFoundation
import AVKit
import MediaPlayer

struct AVPlayerLayerView: UIViewRepresentable {
    let player: AVPlayer

    final class PlayerUIView: UIView {
        override static var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }

    func makeUIView(context: Context) -> PlayerUIView {
        let v = PlayerUIView()
        v.backgroundColor = .black
        v.playerLayer.player = player
        v.playerLayer.videoGravity = .resizeAspect
        return v
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.playerLayer.player = player
    }
}

struct BuiltInVideoPlayerView: View {
    let videoUrl: String
    let pageTitle: String
    let onClose: () -> Void
    let onDownload: (String) -> Void

    @State private var player: AVPlayer?
    @State private var isPlaying = true
    @State private var isFullscreen = false
    @State private var controlsVisible = true
    @State private var currentSec: Double = 0
    @State private var durationSec: Double = 0
    @State private var isSeeking = false
    @State private var playbackSpeed: Float = 1.0

    @State private var offset: CGSize = .zero
    @State private var dragStartOffset: CGSize = .zero
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0

    private let speeds: [Float] = [1.0, 1.25, 1.5, 2.0]

    var body: some View {
        GeometryReader { geo in
            let w = isFullscreen ? geo.size.width : min(geo.size.width - 24, 300 * scale)
            let h = isFullscreen ? geo.size.height : w * 9.0 / 16.0

            ZStack {
                if let p = player {
                    AVPlayerLayerView(player: p)
                        .frame(width: w, height: h)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            withAnimation(.easeInOut(duration: 0.18)) {
                                controlsVisible.toggle()
                            }
                        }
                } else {
                    Color.black.frame(width: w, height: h)
                    ProgressView().tint(.white)
                }

                if controlsVisible {
                    ZStack {
                        // Top bar
                        VStack {
                            HStack(spacing: 8) {
                                Button(action: onClose) {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                        .frame(width: 26, height: 26)
                                }
                                Text(pageTitle.isEmpty ? "视频播放" : pageTitle)
                                    .font(.system(size: isFullscreen ? 14 : 11, weight: .medium))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                Spacer()
                                Button {
                                    let idx = (speeds.firstIndex(of: playbackSpeed) ?? 0 + 1) % speeds.count
                                    playbackSpeed = speeds[idx]
                                    player?.rate = isPlaying ? playbackSpeed : 0
                                } label: {
                                    Text(playbackSpeed == 1.0 ? "1x" : "\(playbackSpeed)x")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(Color.white.opacity(0.2))
                                        .clipShape(Capsule())
                                }
                            }
                            .padding(.horizontal, isFullscreen ? 28 : 8)
                            .padding(.top, isFullscreen ? 14 : 6)
                            .padding(.bottom, 6)
                            .background(LinearGradient(colors: [.black.opacity(0.72), .clear], startPoint: .top, endPoint: .bottom))
                            Spacer()
                        }

                        // Center Play/Pause
                        Button {
                            guard let p = player else { return }
                            if isPlaying {
                                p.pause()
                                isPlaying = false
                            } else {
                                p.play()
                                p.rate = playbackSpeed
                                isPlaying = true
                            }
                        } label: {
                            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: isFullscreen ? 26 : 18))
                                .foregroundColor(.white)
                                .frame(width: isFullscreen ? 54 : 38, height: isFullscreen ? 54 : 38)
                                .background(Color.black.opacity(0.5))
                                .clipShape(Circle())
                        }

                        // Middle-Right Download icon (compact 24pt in floating mode)
                        HStack {
                            Spacer()
                            Button {
                                onDownload(videoUrl)
                            } label: {
                                Image(systemName: "arrow.down.to.line")
                                    .font(.system(size: isFullscreen ? 16 : 11, weight: .semibold))
                                    .foregroundColor(.white)
                                    .frame(width: isFullscreen ? 40 : 24, height: isFullscreen ? 40 : 24)
                                    .background(Color.black.opacity(0.45))
                                    .clipShape(Circle())
                            }
                            .padding(.trailing, isFullscreen ? 28 : 6)
                        }

                        // Bottom bar with slim seekbar
                        VStack {
                            Spacer()
                            HStack(spacing: 8) {
                                Text("\(formatSec(currentSec))/\(formatSec(durationSec))")
                                    .font(.system(size: isFullscreen ? 12 : 10, design: .monospaced))
                                    .foregroundColor(.white)
                                Slider(
                                    value: Binding(
                                        get: { durationSec > 0 ? currentSec / durationSec : 0 },
                                        set: { ratio in
                                            isSeeking = true
                                            currentSec = ratio * durationSec
                                        }
                                    ),
                                    onEditingChanged: { editing in
                                        isSeeking = editing
                                        if !editing, durationSec > 0 {
                                            let target = CMTime(seconds: currentSec, preferredTimescale: 600)
                                            player?.seek(to: target)
                                        }
                                    }
                                )
                                .tint(.white)

                                Button {
                                    withAnimation(.spring()) { isFullscreen.toggle() }
                                } label: {
                                    Image(systemName: isFullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                            }
                            .padding(.horizontal, isFullscreen ? 28 : 8)
                            .padding(.bottom, isFullscreen ? 14 : 6)
                            .padding(.top, 4)
                            .background(LinearGradient(colors: [.clear, .black.opacity(0.78)], startPoint: .top, endPoint: .bottom))
                        }
                    }
                    .frame(width: w, height: h)
                }
            }
            .frame(width: w, height: h)
            .clipShape(RoundedRectangle(cornerRadius: isFullscreen ? 0 : 14, style: .continuous))
            .shadow(radius: isFullscreen ? 0 : 10)
            .position(
                x: isFullscreen ? geo.size.width / 2 : (geo.size.width / 2 + offset.width),
                y: isFullscreen ? geo.size.height / 2 : (geo.size.height - h / 2 - 70 + offset.height)
            )
            .gesture(
                isFullscreen ? nil :
                SimultaneousGesture(
                    DragGesture()
                        .onChanged { v in
                            offset = CGSize(width: dragStartOffset.width + v.translation.width,
                                            height: dragStartOffset.height + v.translation.height)
                        }
                        .onEnded { _ in dragStartOffset = offset },
                    MagnificationGesture()
                        .onChanged { m in scale = min(max(lastScale * m, 0.5), 1.4) }
                        .onEnded { _ in lastScale = scale }
                )
            )
        }
        .onAppear { startPlayer() }
        .onDisappear {
            player?.pause()
            player = nil
        }
    }

    private func startPlayer() {
        guard let url = URL(string: videoUrl) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        let p = AVPlayer(url: url)
        player = p
        p.play()

        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = pageTitle.isEmpty ? "Jools 视频播放" : pageTitle
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info

        p.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { time in
            if !isSeeking {
                currentSec = time.seconds.isFinite ? time.seconds : 0
            }
            if let dur = p.currentItem?.duration.seconds, dur.isFinite && dur > 0 {
                durationSec = dur
            }
        }
    }

    private func formatSec(_ s: Double) -> String {
        let total = max(0, Int(s))
        let m = total / 60
        let sec = total % 60
        return String(format: "%02d:%02d", m, sec)
    }
}
