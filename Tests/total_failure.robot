***Settings***
Library    RequestsLibrary
Library    Collections
Library    JSONLibrary

Force Tags  Total_Failure


***Variables***
${SWAPI_BASE_URL} =    https://swapi.info/api

***Test Cases***
Verify Species Name Is Incorrect (Failing)
    [Documentation]    This test is designed to fail by asserting an incorrect species name.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /species/3/  # Wookiee
    Status Should Be    200    ${resp}
    ${name} =    Get Value From Json    ${resp.json()}    $.name
    Should Be Equal    ${name}    Ewok    msg=Expected Ewok, but found ${name}. This test should fail.
    Log To Console    Attempted to verify incorrect species name.