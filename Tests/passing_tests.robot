***Settings***
Library    RequestsLibrary
Library    Collections
Library    JSONLibrary

Force Tags  Passing_Suite


***Variables***
${SWAPI_BASE_URL} =    https://swapi.info/api

***Test Cases***
Verify Planets Endpoint Returns Success
    [Documentation]    Checks if the /planets endpoint returns a 200 OK status.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /planets
    Status Should Be    200    ${resp}
    Log To Console    Successfully retrieved planets.

Verify Luke Skywalker Exists
    [Documentation]    Checks if Luke Skywalker is present in the /people endpoint.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /people/1/  # Luke Skywalker's ID
    Status Should Be    200    ${resp}
    ${name} =    Get Value From Json    ${resp.json()}    $.name
    ${json_body} =    Convert String To Json    ${resp.content}
    # Esta es una forma más directa de obtener el primer elemento de la lista
    ${name} =          Get Value From Json    ${json_body}    $.name
    ${name} =          Set Variable    ${name}[0]
    Should Be Equal    ${name}    Luke Skywalker
    Log To Console    Luke Skywalker found.

Verify Film Endpoint Returns Success
    [Documentation]    Checks if a specific film endpoint returns 200 OK.
    Create Session    swapi    ${SWAPI_BASE_URL}
    ${resp} =    GET On Session    swapi    /films/1/  # A New Hope
    Status Should Be    200    ${resp}
    Log To Console    Film endpoint is accessible.