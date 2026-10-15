// Compares a GoogleTest JSON report (--gtest_output=json:FILE) with the
// expected outcome of every test: node check-gtest.js <report> <expected>.
// Expected outcomes are "pass", "fail" or "skip", by test suite and name, or
// { "outcome": "fail", "message": "..." } for a failure whose message is
// checked too. --no-messages compares outcomes only, for builds without a
// Java runtime, whose assertion messages cannot show Java values.
const fs = require("fs");
const path = require("path");

/** The { outcome, message } an expected-results.json entry describes */
function expectedOutcome(entry) {
	return typeof entry === "string" ? { outcome: entry } : entry;
}

/**
 * The differences between actual results ({ suite: { test: { outcome,
 * message } } }) and the expected ones; messages are compared where both
 * the expectation and the actual result have one.
 */
function compare(actual, expected, checkMessages = true) {
	const problems = [];
	for (const [suite, tests] of Object.entries(expected)) {
		for (const [test, entry] of Object.entries(tests)) {
			const want = expectedOutcome(entry);
			const got = actual[suite] && actual[suite][test];
			if (!got) {
				problems.push(`${suite}.${test}: did not run (expected ${want.outcome})`);
			} else if (got.outcome !== want.outcome) {
				problems.push(`${suite}.${test}: ${got.outcome}, expected ${want.outcome}`
					+ (got.message ? ` (${got.message})` : ""));
			} else if (checkMessages && want.message !== undefined && got.message !== want.message) {
				problems.push(`${suite}.${test}: message ${JSON.stringify(got.message)}, expected ${JSON.stringify(want.message)}`);
			}
		}
	}
	for (const [suite, tests] of Object.entries(actual)) {
		for (const test of Object.keys(tests)) {
			if (!expected[suite] || !(test in expected[suite])) {
				problems.push(`${suite}.${test}: not in the expected results`);
			}
		}
	}
	return problems;
}

/**
 * The results in a GoogleTest JSON report. The wrappers report a Java
 * exception as "Test threw <class>: <message>", whose message is that
 * text, and JUnit 4's expected exceptions with JUnit's own messages.
 */
function gtestResults(report) {
	const actual = {};
	for (const suite of report.testsuites) {
		for (const test of suite.testsuite) {
			let result;
			if (test.result === "SKIPPED") {
				result = { outcome: "skip" };
			} else if (test.failures && test.failures.length > 0) {
				// "<file>:<line>\nFailed\n<text>"
				const text = test.failures[0].failure.replace(/^[^\n]*\nFailed\n/, "").replace(/\n$/, "");
				const thrown = text.match(/^Test threw [\w.$]+(?:: ([\s\S]*))?$/);
				result = { outcome: "fail", message: thrown ? thrown[1] || "" : text };
			} else {
				result = { outcome: "pass" };
			}
			actual[suite.name] = actual[suite.name] || {};
			actual[suite.name][test.name] = result;
		}
	}
	return actual;
}

/** The expected results of fixture directories, each with expected-results.json */
function readExpected(directories) {
	const expected = {};
	for (const directory of directories) {
		Object.assign(expected, JSON.parse(fs.readFileSync(path.join(directory, "expected-results.json"), "utf8")));
	}
	return expected;
}

module.exports = { compare, expectedOutcome, gtestResults, readExpected };

if (require.main === module) {
	const args = process.argv.slice(2);
	const checkMessages = !args.includes("--no-messages");
	const [reportFile, expectedFile] = args.filter((arg) => !arg.startsWith("--"));
	const report = JSON.parse(fs.readFileSync(reportFile, "utf8"));
	const expected = JSON.parse(fs.readFileSync(expectedFile, "utf8"));
	const problems = compare(gtestResults(report), expected, checkMessages);
	if (problems.length > 0) {
		console.error(problems.join("\n"));
		console.error("GoogleTest outcomes differ from the expected outcomes");
		process.exit(1);
	}
	const count = Object.values(expected).reduce((sum, tests) => sum + Object.keys(tests).length, 0);
	const messages = checkMessages
		? Object.values(expected).reduce((sum, tests) =>
			sum + Object.values(tests).filter((entry) => expectedOutcome(entry).message !== undefined).length, 0)
		: 0;
	console.log(`GoogleTest outcomes match for ${count} test(s)` + (messages ? `, and ${messages} failure message(s)` : ""));
}
