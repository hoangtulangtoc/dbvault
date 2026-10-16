<#-- THIRD-PARTY.txt for the application jar (docs/licences.md). dbvault's own modules are left out:
     they are not third-party, and the licence their POMs inherit from Spring Boot's parent is not
     a licence dbvault has chosen. Lombok is left out because it only runs at compile time and the
     Spring Boot plugin does not package it. -->
<#assign bundled = []/>
<#list dependencyMap as e>
    <#if e.getKey().groupId != "dev.dbvault" && e.getKey().artifactId != "lombok">
        <#assign bundled = bundled + [e]/>
    </#if>
</#list>
Third-party libraries in the dbvault application jar, with the licences their own POMs declare.
Where a library offers a choice of licences, every one it names is listed.

${bundled?size} libraries.

<#list bundled as e>
<#assign p = e.getKey()/>
${p.groupId}:${p.artifactId}:${p.version}<#list e.getValue() as licence>  [${licence}]</#list>
</#list>
