pipeline {
    agent any

    stages {
        stage('Checkout SCM') {
            steps {
                script {
                    echo 'Cloning the test repository...'
                    // Replace with your actual GitHub repo URL and credentials
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

                    // Add a descriptive name for the test run in reports
                    robotCommand += " --name \"SWAPI Tests\""

                    // Append the target test file/directory
                    robotCommand += " Tests/"

                    bat "${robotCommand} || exit 0" // Usar 'exit 0' es más robusto para que el shell siempre retorne 0
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
print("--- Python Script Output Start ---") # <-- Nueva línea
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
        print("ROBOT_FAILED_PERCENTAGE=0.00")
        sys.exit(0) # Salir limpiamente si no se encuentra el archivo, sin levantar excepción

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
        # Fallback for older Robot Framework versions or different XML structures
        for test_element in root.findall('.//test'):
            total_tests += 1
            status = test_element.find('status')
            if status is not None and status.get('status') == 'PASS':
                passed_tests += 1
            else:
                failed_tests += 1

    failed_percentage = (failed_tests / total_tests) * 100 if total_tests > 0 else 0.0

    print(f"ROBOT_TOTAL_TESTS={total_tests}")
    print(f"ROBOT_PASSED_TESTS={passed_tests}")
    print(f"ROBOT_FAILED_TESTS={failed_tests}")
    print(f"ROBOT_FAILED_PERCENTAGE={failed_percentage:.2f}")

except Exception as e: # Capturamos cualquier otra excepción que no sea FileNotFoundError
    sys.stderr.write(f"Error parsing XML: {e}. Setting default values.\\n")
    print("ROBOT_TOTAL_TESTS=0")
    print("ROBOT_PASSED_TESTS=0")
    print("ROBOT_FAILED_TESTS=0")
    print("ROBOT_FAILED_PERCENTAGE=0.00")
    sys.exit(0) # Salir limpiamente incluso en caso de otros errores de parseo
"""
                    writeFile file: pythonScriptFileName, text: pythonScriptContent

                    def commandResult = bat(script: "python ${pythonScriptFileName}", returnStdout: true, returnStatus: true)

                    def scriptOutput
                    if (commandResult.class.name == 'org.jenkinsci.plugins.pipeline.modeldefinition.steps.impl.ScriptStepExecution$BatResult') {
                        scriptOutput = commandResult.stdout
                    } else {
                        echo "WARNING: commandResult was not a BatResult object. It was: ${commandResult.class.name}"
                        scriptOutput = ""
                    }

                    echo "Raw Python Script Output:\n${scriptOutput}"

                    scriptOutput.trim().eachLine { line ->
                        if (line.contains("ROBOT_")) {
                            def parts = line.split("=", 2)
                            if (parts.size() == 2) {
                                env."${parts[0].trim()}" = parts[1].trim()
                            }
                        }
                    }
                    echo "Extracted Results: Total: ${env.ROBOT_TOTAL_TESTS}, Passed: ${env.ROBOT_PASSED_TESTS}, Failed: ${env.ROBOT_FAILED_TESTS}, Failed %: ${env.ROBOT_FAILED_PERCENTAGE}%"
                }
            }
        }

        stage('Archive Reports') {
            steps {
                script {
                    echo 'Archiving HTML reports...'
                    archiveArtifacts artifacts: 'reports/*.html', fingerprinter: true
                }
            }
        }

        stage('Send Status to Tines') {
            steps {
                script {
                    echo 'Sending test status to Tines...'
                    // Determine status based on parsed results.
                    def finalStatus = (env.ROBOT_FAILED_TESTS == '0') ? 'SUCCESS' : 'FAILED'
                    def payload = """
                    {
                        "jobName": "${env.JOB_NAME}",
                        "buildNumber": "${env.BUILD_NUMBER}",
                        "pipelineStatus": "${currentBuild.result}", // Jenkins' overall build status (SUCCESS/FAILURE/UNSTABLE)
                        "testStatus": "${finalStatus}", // Calculated Robot test status
                        "reportUrl": "${env.BUILD_URL}artifact/reports/output.html",
                        "robotTagsUsed": "${params.ROBOT_TAGS}",
                        "totalTests": "${env.ROBOT_TOTAL_TESTS}",
                        "passedTests": "${env.ROBOT_PASSED_TESTS}",
                        "failedTests": "${env.ROBOT_FAILED_TESTS}",
                        "failedPercentage": "${env.ROBOT_FAILED_PERCENTAGE}"
                    }
                    """
                    if (params.TINES_WEBHOOK_URL != null && params.TINES_WEBHOOK_URL.trim() != '') {
                        powershell """
                        \$headers = @{ "Content-Type" = "application/json" }
                        \$body = '${payload}' | ConvertFrom-Json # Convertir el string Groovy a un objeto JSON
                        Invoke-RestMethod -Uri '${params.TINES_WEBHOOK_URL}' -Method Post -Headers \$headers -Body \$body
                        """
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