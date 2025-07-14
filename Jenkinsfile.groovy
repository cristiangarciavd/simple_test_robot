import groovy.json.JsonOutput

pipeline {
    agent any

    stages {
        stage('Checkout SCM') {
            steps {
                script {
                    echo 'Cloning the test repository...'
                    git branch: 'main', url: 'https://github.com/cristiangarciavd/simple_test_robot.git'
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                script {
                    echo 'Installing Python dependencies...'
                    bat 'pip install -r requirements.txt'
                }
            }
        }

        stage('Run Robot Framework Tests') {
            steps {
                script {
                    echo "Executing Robot Framework tests."
                    def robotCommand = "robot --outputdir reports"

                    if (params.ROBOT_TAGS != null && params.ROBOT_TAGS.trim() != '') {
                        robotCommand += " --include ${params.ROBOT_TAGS}"
                        echo "Including tests with tags: ${params.ROBOT_TAGS}"
                    }

                    robotCommand += " --name \"SWAPI Tests\""
                    robotCommand += " Tests/"
                    bat "${robotCommand} || exit 0"
                }
            }
        }

        stage('Extract Test Results from XML') {
            steps {
                script {
                    echo 'Extracting detailed test results from output.xml...'
                    def outputXmlPath = "reports/output.xml"
                    def pythonScriptFileName = "parse_robot_output.py"
                    def pythonScriptContent = """
print("--- Python Script Output Start ---")
import xml.etree.ElementTree as ET
import sys
import os

output_xml_path = '${outputXmlPath}'

try:
    if not os.path.exists(output_xml_path):
        sys.stderr.write(f"Error: output.xml not found at {output_xml_path}. Setting default values.\\n")
        print("ROBOT_TOTAL_TESTS=0")
        print("ROBOT_PASSED_TESTS=0")
        print("ROBOT_FAILED_TESTS=0")
        print("ROBOT_FAILED_PERCENTAGE=0")
        sys.exit(0)

    tree = ET.parse(output_xml_path)
    root = tree.getroot()

    total_tests = 0
    passed_tests = 0
    failed_tests = 0

    stats_element = root.find('.//statistics/total/stat[@id="s1"]')
    if stats_element is not None:
        total_tests = int(stats_element.get('total', 0))
        passed_tests = int(stats_element.get('pass', 0))
        failed_tests = int(stats_element.get('fail', 0))
    else:
        for test_element in root.findall('.//test'):
            total_tests += 1
            status = test_element.find('status')
            if status is not None and status.get('status') == 'PASS':
                passed_tests += 1
            else:
                failed_tests += 1

    failed_percentage = int((failed_tests / total_tests) * 100) if total_tests > 0 else 0

    print(f"ROBOT_TOTAL_TESTS={total_tests}")
    print(f"ROBOT_PASSED_TESTS={passed_tests}")
    print(f"ROBOT_FAILED_TESTS={failed_tests}")
    print(f"ROBOT_FAILED_PERCENTAGE={failed_percentage}")

except Exception as e:
    sys.stderr.write(f"Error parsing XML: {e}. Setting default values.\\n")
    print("ROBOT_TOTAL_TESTS=0")
    print("ROBOT_PASSED_TESTS=0")
    print("ROBOT_FAILED_TESTS=0")
    print("ROBOT_FAILED_PERCENTAGE=0")
    sys.exit(0)
"""
                    writeFile file: pythonScriptFileName, text: pythonScriptContent

                    def scriptOutput = bat(script: "python ${pythonScriptFileName}", returnStdout: true)

                    echo "Raw Python Script Output:\n${scriptOutput}"

                    def parsedResults = [:]
                    if (scriptOutput) {
                        scriptOutput.split('\n').each { line ->
                            if (line.contains("ROBOT_")) {
                                def parts = line.split("=", 2)
                                if (parts.size() == 2) {
                                    parsedResults[parts[0].trim()] = parts[1].trim()
                                }
                            }
                        }
                    } else {
                        echo "WARNING: Python script output was empty."
                    }

                    env.ROBOT_TOTAL_TESTS = parsedResults.get('ROBOT_TOTAL_TESTS', '0')
                    env.ROBOT_PASSED_TESTS = parsedResults.get('ROBOT_PASSED_TESTS', '0')
                    env.ROBOT_FAILED_TESTS = parsedResults.get('ROBOT_FAILED_TESTS', '0')
                    env.ROBOT_FAILED_PERCENTAGE = parsedResults.get('ROBOT_FAILED_PERCENTAGE', '0')

                    echo "Extracted Results: Total: ${env.ROBOT_TOTAL_TESTS}, Passed: ${env.ROBOT_PASSED_TESTS}, Failed: ${env.ROBOT_FAILED_TESTS}, Failed %: ${env.ROBOT_FAILED_PERCENTAGE}%"
                }
            }
        }

        stage('Archive Reports') {
            steps {
                script {
                    echo 'Archiving HTML reports...'
                    archiveArtifacts artifacts: 'reports/*.html'
                }
            }
        }
        stage('Send Status to Tines') {
            steps {
                script {
                    echo 'Sending test status to Tines...'
                    def finalStatus = (env.ROBOT_FAILED_TESTS == '0') ? 'SUCCESS' : 'FAILED'

                    def totalTestsNum = env.ROBOT_TOTAL_TESTS.toInteger()
                    def passedTestsNum = env.ROBOT_PASSED_TESTS.toInteger()
                    def failedTestsNum = env.ROBOT_FAILED_TESTS.toInteger()
                    def failedPercentageNum = env.ROBOT_FAILED_PERCENTAGE.toInteger()

                    def payloadMap = [
                        jobName: env.JOB_NAME,
                        buildNumber: env.BUILD_NUMBER,
                        testStatus: finalStatus,
                        reportUrl: "${env.BUILD_URL}artifact/reports/log.html",
                        robotTagsUsed: params.ROBOT_TAGS,
                        totalTests: totalTestsNum,
                        passedTests: passedTestsNum,
                        failedTests: failedTestsNum,
                        failedPercentage: failedPercentageNum
                    ]

                    def payload = JsonOutput.toJson(payloadMap)

                    echo "payload: ${payload}"
                    if (params.TINES_WEBHOOK_URL != null && params.TINES_WEBHOOK_URL.trim() != '') {
                        def payloadFileName = "tines_payload.json"
                        writeFile file: payloadFileName, text: payload

                        bat "curl -X POST -H \"Content-Type: application/json\" -d @${payloadFileName} \"${params.TINES_WEBHOOK_URL}\""
                    } else {
                        echo "WARNING! Missing TINES_WEBHOOK_URL, so results were not sent."
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        failure {
            script {
                echo "Pipeline failed!"
            }
        }
    }
}