***Settings***
Library    RequestsLibrary
Library    Collections
Library    JSONLibrary

Force Tags  Failing_Suite


***Variables***
${SWAPI_BASE_URL} =    https://swapi.info/api

***Test Cases***
Verify Existing Film Returns Success (Passing)
    [Documentation]    Checks an existing film endpoint, this test should pass.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /films/2/  # The Empire Strikes Back
    Status Should Be    200    ${resp}
    Log To Console    Existing film endpoint is accessible.

Verify Non-Existent Endpoint Returns 404 (Passing)
    [Documentation]    Checks if a non-existent endpoint correctly returns a 404.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /this-endpoint-does-not-exist/  expected_status=404
    Log To Console    Non-existent endpoint correctly returned 404.

Verify Species Name Is Incorrect (Failing)
    [Documentation]    This test is designed to fail by asserting an incorrect species name.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /species/3/  # Wookiee
    Status Should Be    200    ${resp}
    ${name} =    Get Value From Json    ${resp.json()}    $.name
    Should Be Equal    ${name}    Ewok    msg=Expected Ewok, but found ${name}. This test should fail.
    Log To Console    Attempted to verify incorrect species name.