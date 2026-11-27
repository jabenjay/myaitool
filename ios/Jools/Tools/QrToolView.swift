import SwiftUI

struct QrToolView: View {
    @ObservedObject var prefs: AppPreferences
    @State private var inputText = "https://github.com/jabenjay/myaitool"
    @State private var qrImage: UIImage?
    @State private var toastMsg: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("二维码内容").font(.system(size: 13, weight: .semibold))
                    TextEditor(text: $inputText)
                        .frame(minHeight: 90)
                        .padding(6)
                        .background(Color.primary.opacity(0.04))
                        .cornerRadius(12)
                }
                .padding(14)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)

                Button {
                    qrImage = JoolsQRGenerator.makeQRImage(from: inputText, size: 720)
                } label: {
                    Text("生成二维码")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .disabled(inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                if let img = qrImage {
                    VStack(spacing: 12) {
                        Image(uiImage: img)
                            .interpolation(.none)
                            .resizable()
                            .frame(width: 220, height: 220)
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(18)

                        Button {
                            UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                            toastMsg = "已保存到相册"
                        } label: {
                            Label("保存二维码图片", systemImage: "square.and.arrow.down")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding(16)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
                }
            }
            .padding(16)
        }
        .navigationTitle("二维码生成")
        .onAppear {
            if qrImage == nil {
                qrImage = JoolsQRGenerator.makeQRImage(from: inputText, size: 720)
            }
        }
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }
}
