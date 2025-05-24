//
// Created by kokolor on 24/05/25.
//

#include <stdio.h>
#include "errors.h"

void error(const int line, const char* error)
{
    printf("[%d] %s\n", line, error);
}
