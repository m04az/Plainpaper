import XCTest
import ComposeApp

/// Device benchmark (spec 002, research R16). Bundle photos under `iosAppTests/BenchPhotos/`
/// (any JPEG) and run this test on the iPhone from Xcode; the JSON report is printed to the console
/// and attached to the test result. CI only compiles this target.
final class ScanBenchmarkTests: XCTestCase {
    func testBenchmarkBundledPhotos() throws {
        let bundle = Bundle(for: ScanBenchmarkTests.self)
        let paths = (bundle.urls(forResourcesWithExtension: "jpg", subdirectory: nil) ?? []).map { $0.path }.sorted()
        try XCTSkipIf(paths.isEmpty, "No bench photos bundled; add JPEGs to iosAppTests/BenchPhotos")
        let report = ScanBenchmark.shared.run(paths: paths, iterations: 2)
        print("SCAN BENCHMARK REPORT\n\(report)")
        let attachment = XCTAttachment(string: report)
        attachment.name = "bench.json"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertFalse(report.contains("\"failures\":0") == false, "benchmark reported failures: \(report)")
    }
}
