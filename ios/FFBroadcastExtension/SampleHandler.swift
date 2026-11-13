import ReplayKit

/// Experimental ReplayKit extension.
/// Does not save, transmit or export samples. A secure authenticated
/// receiver and explicit session pairing must be added before streaming.
final class SampleHandler: RPBroadcastSampleHandler {
    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        // Explicitly started by the system broadcast picker.
    }

    override func broadcastPaused() {}

    override func broadcastResumed() {}

    override func broadcastFinished() {}

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer,
                                      with sampleBufferType: RPSampleBufferType) {
        // Intentionally no-op until secure transport is implemented.
    }
}
