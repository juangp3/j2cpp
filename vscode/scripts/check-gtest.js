// Compares a GoogleTest JSON report (--gtest_output=json:FILE) with the
// expected outcome of every test: node check-gtest.js <report> <expected>.
// Expected outcomes are "pass", "fail" or "skip", by test suite and name.
const assert = require("assert");
const fs = require("fs");

const [reportFile, expectedFile] = process.argv.slice(2);
const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
const expected = JSON.parse(fs.readFileSync(expectedFile, "utf8"));

const actual = {};
for (const suite of report.testsuites) {
	for (const test of suite.testsuite) {
		const outcome = test.result === "SKIPPED"
			? "skip"
			: (test.failures && test.failures.length > 0 ? "fail" : "pass");
		actual[suite.name] = actual[suite.name] || {};
		actual[suite.name][test.name] = outcome;
	}
}

assert.deepStrictEqual(actual, expected, "GoogleTest outcomes differ from the expected outcomes");
const count = Object.values(expected).reduce((sum, tests) => sum + Object.keys(tests).length, 0);
console.log(`GoogleTest outcomes match for ${count} test(s)`);
